package com.seniorinsight.identity.application.pareamento;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.Tokens;
import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento.Consumo;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.domain.evento.UserCreated;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import com.seniorinsight.identity.domain.pareamento.Plataforma;
import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Resgata o código no aparelho do idoso (HU-007) e devolve a sessão, conforme docs/adr/0001-token-do-idoso.md.
 *
 * <p>Ordem: consome o código no Redis (apaga e marca como resgatado numa operação só, então dois resgates
 * simultâneos não passam), garante o usuário do idoso no Keycloak e, numa transação, cria o perfil IDOSO (com
 * {@code user.created} na outbox), registra a {@code sessao_aparelho} e faz o token exchange. Se algo falhar
 * depois do consumo, a transação volta e o código é devolvido ao Redis para o idoso tentar de novo.
 */
@Service
public class ResgatarCodigoPareamento {

    private static final Logger log = LoggerFactory.getLogger(ResgatarCodigoPareamento.class);

    private final RepositorioCodigosPareamento codigos;
    private final ProvedorIdentidade provedor;
    private final RepositorioPerfis perfis;
    private final RepositorioSessoesAparelho sessoes;
    private final PublicadorEventos publicador;
    private final TransactionOperations transacao;
    private final PropriedadesPareamento propriedades;

    public ResgatarCodigoPareamento(RepositorioCodigosPareamento codigos, ProvedorIdentidade provedor,
                                    RepositorioPerfis perfis, RepositorioSessoesAparelho sessoes,
                                    PublicadorEventos publicador, TransactionOperations transacao,
                                    PropriedadesPareamento propriedades) {
        this.codigos = codigos;
        this.provedor = provedor;
        this.perfis = perfis;
        this.sessoes = sessoes;
        this.publicador = publicador;
        this.transacao = transacao;
        this.propriedades = propriedades;
    }

    public Resultado executar(Comando comando) {
        // Microssegundos: a precisão do TIMESTAMPTZ, para o perfil devolvido ser igual ao relido do banco depois.
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        CodigoPareamento codigo = switch (codigos.consumir(comando.codigo(), agora, propriedades.retencao())) {
            case Consumo.Resgatado resgatado -> resgatado.codigo();
            case Consumo.JaResgatado ignorado -> throw new CodigoJaResgatado();
            case Consumo.Expirado ignorado -> throw new CodigoExpirado();
            case Consumo.Inexistente ignorado -> throw new CodigoNaoEncontrado();
        };
        try {
            String sub = provedor.obterOuCriarUsuarioIdoso(chaveDoIdoso(codigo), codigo.nomeIdoso());
            return transacao.execute(status -> registrar(sub, codigo, comando, agora));
        } catch (RuntimeException e) {
            try {
                codigos.devolver(codigo, propriedades.retencao());
            } catch (RuntimeException falhaAoDevolver) {
                e.addSuppressed(falhaAoDevolver);
            }
            throw e;
        }
    }

    private Resultado registrar(String sub, CodigoPareamento codigo, Comando comando, Instant agora) {
        Perfil perfil = perfis.buscarPorKeycloakSub(sub)
                .map(existente -> completarIdosoId(existente, codigo, agora))
                .orElseGet(() -> criarPerfil(sub, codigo, agora));
        SessaoAparelho sessao = SessaoAparelho.nova(perfil.id(), comando.identificadorAparelho(),
                comando.plataforma(), codigo.familiarSub(), agora);
        sessoes.registrar(sessao);
        // Por último: se o Keycloak recusar, a transação volta e não fica sessão sem token.
        Tokens tokens = provedor.emitirTokens(sub);
        return new Resultado(perfil, sessao.id(), codigo.idosoId(), tokens);
    }

    /**
     * Perfil criado antes de o {@code idosoId} existir (ou de o código trazê-lo): preenche o campo, só se estiver vazio,
     * e publica {@code user.created} de novo, com o {@code idosoId}, para o report-service identificar o titular. Um
     * {@code idosoId} diferente do gravado não é sobrescrito.
     */
    private Perfil completarIdosoId(Perfil existente, CodigoPareamento codigo, Instant agora) {
        UUID doCodigo = codigo.idosoId();
        if (doCodigo == null || doCodigo.equals(existente.idosoId())) {
            return existente;
        }
        if (existente.idosoId() != null) {
            log.warn("O perfil {} já tem o idosoId {}; o idosoId {} do código de pareamento não foi gravado",
                    existente.id(), existente.idosoId(), doCodigo);
            return existente;
        }
        Perfil preenchido = existente.comIdosoId(doCodigo, agora);
        if (!perfis.preencherIdosoId(preenchido)) {
            return existente;
        }
        publicador.publicar(UserCreated.de(preenchido));
        return preenchido;
    }

    private Perfil criarPerfil(String sub, CodigoPareamento codigo, Instant agora) {
        Perfil novo = Perfil.novoIdoso(sub, codigo.nomeIdoso(), codigo.idosoId(), agora);
        if (perfis.inserirSeAusente(novo)) {
            publicador.publicar(UserCreated.de(novo));
            return novo;
        }
        return perfis.buscarPorKeycloakSub(sub).orElseThrow(() -> new IllegalStateException(
                "Perfil de " + sub + " não foi inserido nem encontrado"));
    }

    /**
     * Com {@code idosoId}, o mesmo idoso pareado de novo reaproveita o usuário e o perfil. Sem ele, a chave é fixa
     * para este código: se o resgate falhar e o código for devolvido, a nova tentativa reaproveita o usuário já criado.
     */
    static UUID chaveDoIdoso(CodigoPareamento codigo) {
        if (codigo.idosoId() != null) {
            return codigo.idosoId();
        }
        String semente = "pareamento:" + codigo.codigo() + ":" + codigo.familiarSub() + ":" + codigo.expiraEm();
        return UUID.nameUUIDFromBytes(semente.getBytes(StandardCharsets.UTF_8));
    }

    public record Comando(String codigo, String identificadorAparelho, Plataforma plataforma) {

        public Comando {
            Objects.requireNonNull(codigo, "codigo");
            Objects.requireNonNull(identificadorAparelho, "identificadorAparelho");
            Objects.requireNonNull(plataforma, "plataforma");
        }
    }

    /** {@code idosoId} é o id do idoso no care-service informado ao gerar o código, ou null quando não houve. */
    public record Resultado(Perfil perfil, UUID sessaoAparelhoId, UUID idosoId, Tokens tokens) {
    }
}
