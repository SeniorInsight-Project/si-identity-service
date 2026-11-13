package com.seniorinsight.identity.adapters.out.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Códigos de pareamento no Redis. Cada operação é um script Lua, então roda atômica no servidor.
 *
 * <ul>
 *   <li>{@code identity:pairing:{codigo}}: hash com o código pendente. Expira em validade + retenção; enquanto
 *       existir depois de vencido, o resgate responde 410.</li>
 *   <li>{@code identity:pairing:resgatado:{codigo}}: marca de código já usado (resgate responde 409).</li>
 *   <li>{@code identity:pairing:emissor:{familiarSub}:{idosoId|novo}}: código vigente de cada familiar para cada
 *       idoso. Gerar outro apaga o anterior (que passa a responder 404).</li>
 * </ul>
 */
@Component
class RepositorioCodigosPareamentoRedis implements RepositorioCodigosPareamento {

    static final String PREFIXO = "identity:pairing:";
    static final String PREFIXO_RESGATADO = PREFIXO + "resgatado:";
    static final String PREFIXO_EMISSOR = PREFIXO + "emissor:";

    /** KEYS: código, marca de resgatado, emissor. ARGV: código, ttl ms, prefixo, sub, nome, idosoId, expiraEm ms. */
    private static final RedisScript<Long> SALVAR = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            local anterior = redis.call('GET', KEYS[3])
            if anterior then
              redis.call('DEL', ARGV[3] .. anterior)
            end
            redis.call('HSET', KEYS[1], 'familiarSub', ARGV[4], 'nomeIdoso', ARGV[5], 'idosoId', ARGV[6],
                       'expiraEm', ARGV[7], 'emissor', KEYS[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            redis.call('SET', KEYS[3], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);

    /** KEYS: código, marca de resgatado. ARGV: código, agora ms, retenção ms. */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CONSUMIR = RedisScript.of("""
            local campos = redis.call('HGETALL', KEYS[1])
            if #campos == 0 then
              if redis.call('EXISTS', KEYS[2]) == 1 then
                return {'RESGATADO'}
              end
              return {'INEXISTENTE'}
            end
            local c = {}
            for i = 1, #campos, 2 do
              c[campos[i]] = campos[i + 1]
            end
            if tonumber(c['expiraEm']) <= tonumber(ARGV[2]) then
              return {'EXPIRADO'}
            end
            redis.call('DEL', KEYS[1])
            if redis.call('GET', c['emissor']) == ARGV[1] then
              redis.call('DEL', c['emissor'])
            end
            redis.call('SET', KEYS[2], '1', 'PX', tonumber(c['expiraEm']) - tonumber(ARGV[2]) + tonumber(ARGV[3]))
            return {'OK', c['familiarSub'], c['nomeIdoso'], c['idosoId'], c['expiraEm']}
            """, List.class);

    /**
     * KEYS: código, marca de resgatado, emissor. ARGV: código, ttl ms, sub, nome, idosoId, expiraEm ms. Só devolve se
     * o código ainda está marcado como resgatado e o familiar não gerou outro nesse meio-tempo.
     */
    private static final RedisScript<Long> DEVOLVER = RedisScript.of("""
            if redis.call('EXISTS', KEYS[2]) == 0 or redis.call('EXISTS', KEYS[3]) == 1 then
              return 0
            end
            redis.call('DEL', KEYS[2])
            redis.call('HSET', KEYS[1], 'familiarSub', ARGV[3], 'nomeIdoso', ARGV[4], 'idosoId', ARGV[5],
                       'expiraEm', ARGV[6], 'emissor', KEYS[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            redis.call('SET', KEYS[3], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);

    /** KEYS: emissor. ARGV: prefixo dos códigos. Apaga o código vigente do emissor e o próprio emissor. */
    private static final RedisScript<Long> EXCLUIR_DO_EMISSOR = RedisScript.of("""
            local codigo = redis.call('GET', KEYS[1])
            if codigo then
              redis.call('DEL', ARGV[1] .. codigo)
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    private static final int LOTE_DA_VARREDURA = 100;
    private static final Pattern CARACTERES_DE_PADRAO = Pattern.compile("[\\\\*?\\[\\]]");
    private static final String SEM_IDOSO = "";

    private final StringRedisTemplate redis;

    RepositorioCodigosPareamentoRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean salvar(CodigoPareamento codigo, Duration retencao) {
        long ttl = Duration.between(Instant.now(), codigo.expiraEm()).plus(retencao).toMillis();
        if (ttl <= 0) {
            throw new IllegalArgumentException("Código " + codigo.codigo() + " já venceu e passou da retenção");
        }
        Long salvo = redis.execute(SALVAR, chaves(codigo),
                codigo.codigo(), Long.toString(ttl), PREFIXO, codigo.familiarSub(), codigo.nomeIdoso(),
                idoso(codigo), Long.toString(codigo.expiraEm().toEpochMilli()));
        return salvo != null && salvo == 1;
    }

    @Override
    public Consumo consumir(String codigo, Instant agora, Duration retencao) {
        List<?> resposta = redis.execute(CONSUMIR, List.of(PREFIXO + codigo, PREFIXO_RESGATADO + codigo),
                codigo, Long.toString(agora.toEpochMilli()), Long.toString(retencao.toMillis()));
        if (resposta == null || resposta.isEmpty()) {
            throw new IllegalStateException("Resposta vazia do Redis ao consumir o código");
        }
        return switch ((String) resposta.getFirst()) {
            case "OK" -> new Consumo.Resgatado(new CodigoPareamento(
                    codigo,
                    (String) resposta.get(1),
                    (String) resposta.get(2),
                    SEM_IDOSO.equals(resposta.get(3)) ? null : UUID.fromString((String) resposta.get(3)),
                    Instant.ofEpochMilli(Long.parseLong((String) resposta.get(4)))));
            case "RESGATADO" -> new Consumo.JaResgatado();
            case "EXPIRADO" -> new Consumo.Expirado();
            case "INEXISTENTE" -> new Consumo.Inexistente();
            default -> throw new IllegalStateException("Resposta inesperada do Redis: " + resposta);
        };
    }

    @Override
    public void devolver(CodigoPareamento codigo, Duration retencao) {
        long ttl = Duration.between(Instant.now(), codigo.expiraEm()).plus(retencao).toMillis();
        if (ttl <= 0) {
            return;
        }
        redis.execute(DEVOLVER, chaves(codigo),
                codigo.codigo(), Long.toString(ttl), codigo.familiarSub(), codigo.nomeIdoso(), idoso(codigo),
                Long.toString(codigo.expiraEm().toEpochMilli()));
    }

    @Override
    public void excluirDoFamiliar(String familiarSub) {
        String padrao = PREFIXO_EMISSOR + CARACTERES_DE_PADRAO.matcher(familiarSub).replaceAll("\\\\$0") + ":*";
        List<String> emissores = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(padrao).count(LOTE_DA_VARREDURA).build())) {
            cursor.forEachRemaining(emissores::add);
        }
        emissores.forEach(emissor -> redis.execute(EXCLUIR_DO_EMISSOR, List.of(emissor), PREFIXO));
    }

    private static List<String> chaves(CodigoPareamento codigo) {
        String idoso = codigo.idosoId() == null ? "novo" : codigo.idosoId().toString();
        return List.of(PREFIXO + codigo.codigo(), PREFIXO_RESGATADO + codigo.codigo(),
                PREFIXO_EMISSOR + codigo.familiarSub() + ":" + idoso);
    }

    private static String idoso(CodigoPareamento codigo) {
        return codigo.idosoId() == null ? SEM_IDOSO : codigo.idosoId().toString();
    }
}
