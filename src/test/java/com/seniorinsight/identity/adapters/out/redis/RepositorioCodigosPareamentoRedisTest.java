package com.seniorinsight.identity.adapters.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento.Consumo;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Scripts Lua contra um Redis real. */
@TesteIntegracao
class RepositorioCodigosPareamentoRedisTest {

    private static final Duration RETENCAO = Duration.ofHours(1);

    @Autowired
    RepositorioCodigosPareamentoRedis repositorio;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void guardaNaChaveIdentityPairingComTtlDeValidadeMaisRetencao() {
        CodigoPareamento codigo = codigo(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(24)));

        assertThat(repositorio.salvar(codigo, RETENCAO)).isTrue();

        String chave = "identity:pairing:" + codigo.codigo();
        assertThat(redis.opsForHash().get(chave, "nomeIdoso")).isEqualTo("Maria");
        assertThat(redis.getExpire(chave)).isBetween(Duration.ofHours(24).toSeconds(), Duration.ofHours(25).toSeconds());
    }

    @Test
    void consumirDevolveOCodigoEApagaDoRedis() {
        UUID idosoId = UUID.randomUUID();
        CodigoPareamento codigo = codigo(idosoId, Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS));
        repositorio.salvar(codigo, RETENCAO);

        Consumo consumo = repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO);

        assertThat(consumo).isEqualTo(new Consumo.Resgatado(codigo));
        assertThat(redis.hasKey("identity:pairing:" + codigo.codigo())).isFalse();
        assertThat(repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO)).isInstanceOf(Consumo.JaResgatado.class);
    }

    @Test
    void codigoSemIdosoVoltaComIdosoIdNulo() {
        CodigoPareamento codigo = codigo(null, Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS));
        repositorio.salvar(codigo, RETENCAO);

        assertThat(repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO)).isEqualTo(new Consumo.Resgatado(codigo));
    }

    @Test
    void codigoVencidoNaoEhConsumido() {
        CodigoPareamento codigo = codigo(null, Instant.now().minus(Duration.ofMinutes(1)));
        repositorio.salvar(codigo, RETENCAO);

        assertThat(repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO)).isInstanceOf(Consumo.Expirado.class);
        assertThat(redis.hasKey("identity:pairing:" + codigo.codigo())).isTrue();
    }

    @Test
    void codigoDesconhecidoEhInexistente() {
        assertThat(repositorio.consumir("QQQQQQ", Instant.now(), RETENCAO)).isInstanceOf(Consumo.Inexistente.class);
    }

    @Test
    void reemitirParaOMesmoIdosoApagaOAnterior() {
        UUID idosoId = UUID.randomUUID();
        CodigoPareamento anterior = codigo(idosoId, Instant.now().plus(Duration.ofHours(1)));
        CodigoPareamento novo = codigo(idosoId, Instant.now().plus(Duration.ofHours(1)));
        repositorio.salvar(anterior, RETENCAO);
        repositorio.salvar(novo, RETENCAO);

        assertThat(repositorio.consumir(anterior.codigo(), Instant.now(), RETENCAO)).isInstanceOf(Consumo.Inexistente.class);
        assertThat(repositorio.consumir(novo.codigo(), Instant.now(), RETENCAO)).isInstanceOf(Consumo.Resgatado.class);
    }

    @Test
    void codigoEmUsoNaoEhSobrescrito() {
        CodigoPareamento codigo = codigo(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)));
        repositorio.salvar(codigo, RETENCAO);

        CodigoPareamento mesmoCodigo = new CodigoPareamento(codigo.codigo(), "outro-familiar", "Outro", null,
                codigo.expiraEm());
        assertThat(repositorio.salvar(mesmoCodigo, RETENCAO)).isFalse();
    }

    @Test
    void devolverPermiteNovaTentativaDoResgate() {
        CodigoPareamento codigo = codigo(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS));
        repositorio.salvar(codigo, RETENCAO);
        repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO);

        repositorio.devolver(codigo, RETENCAO);

        assertThat(repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO)).isEqualTo(new Consumo.Resgatado(codigo));
    }

    @Test
    void devolverNaoRessuscitaCodigoSubstituidoPorUmMaisNovo() {
        UUID idosoId = UUID.randomUUID();
        CodigoPareamento antigo = codigo(idosoId, Instant.now().plus(Duration.ofHours(1)));
        repositorio.salvar(antigo, RETENCAO);
        repositorio.consumir(antigo.codigo(), Instant.now(), RETENCAO);
        repositorio.salvar(codigo(idosoId, Instant.now().plus(Duration.ofHours(1))), RETENCAO);

        repositorio.devolver(antigo, RETENCAO);

        assertThat(repositorio.consumir(antigo.codigo(), Instant.now(), RETENCAO)).isInstanceOf(Consumo.JaResgatado.class);
    }

    @Test
    void resgatesSimultaneosDoMesmoCodigoSoUmPassa() throws Exception {
        CodigoPareamento codigo = codigo(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)));
        repositorio.salvar(codigo, RETENCAO);
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Consumo> chamada = () -> {
            largada.await();
            return repositorio.consumir(codigo.codigo(), Instant.now(), RETENCAO);
        };

        List<Consumo> consumos = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Consumo>> futuros = IntStream.range(0, 8).mapToObj(i -> executor.submit(chamada)).toList();
            largada.countDown();
            for (Future<Consumo> futuro : futuros) {
                consumos.add(futuro.get());
            }
        }

        assertThat(consumos).filteredOn(Consumo.Resgatado.class::isInstance).hasSize(1);
        assertThat(consumos).filteredOn(Consumo.JaResgatado.class::isInstance).hasSize(7);
    }

    @Test
    void excluirDoFamiliarApagaTodosOsCodigosVigentesDeleEDeMaisNinguem() {
        Instant expiraEm = Instant.now().plus(Duration.ofHours(1));
        CodigoPareamento paraIdoso = codigo(UUID.randomUUID(), expiraEm);
        CodigoPareamento paraNovoIdoso = codigo(null, expiraEm);
        CodigoPareamento deOutroFamiliar = new CodigoPareamento(CodigoPareamento.sortear(),
                "sub-" + UUID.randomUUID(), "Joana", null, expiraEm);
        repositorio.salvar(paraIdoso, RETENCAO);
        repositorio.salvar(paraNovoIdoso, RETENCAO);
        repositorio.salvar(deOutroFamiliar, RETENCAO);

        repositorio.excluirDoFamiliar(familiar);

        assertThat(repositorio.consumir(paraIdoso.codigo(), Instant.now(), RETENCAO))
                .isInstanceOf(Consumo.Inexistente.class);
        assertThat(repositorio.consumir(paraNovoIdoso.codigo(), Instant.now(), RETENCAO))
                .isInstanceOf(Consumo.Inexistente.class);
        assertThat(redis.keys("identity:pairing:emissor:" + familiar + ":*")).isEmpty();
        assertThat(repositorio.consumir(deOutroFamiliar.codigo(), Instant.now(), RETENCAO))
                .isInstanceOf(Consumo.Resgatado.class);
    }

    @Test
    void excluirDoFamiliarTrataOSubComoTextoELiteralEnaoComoPadrao() {
        CodigoPareamento deOutroFamiliar = new CodigoPareamento(CodigoPareamento.sortear(),
                "sub-" + UUID.randomUUID(), "Joana", null, Instant.now().plus(Duration.ofHours(1)));
        repositorio.salvar(deOutroFamiliar, RETENCAO);

        repositorio.excluirDoFamiliar("*");

        assertThat(redis.hasKey("identity:pairing:" + deOutroFamiliar.codigo())).isTrue();
    }

    @Test
    void excluirDoFamiliarSemCodigosNaoFazNada() {
        repositorio.excluirDoFamiliar(familiar);
    }

    /** O mesmo familiar em todo o teste (o JUnit cria uma instância por método). */
    private final String familiar = "sub-" + UUID.randomUUID();

    private CodigoPareamento codigo(UUID idosoId, Instant expiraEm) {
        return new CodigoPareamento(CodigoPareamento.sortear(), familiar, "Maria", idosoId, expiraEm);
    }
}
