package com.seniorinsight.identity.application.pareamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.Tokens;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.TokensRenovados;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.domain.pareamento.Plataforma;
import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RenovarSessaoAparelhoTest {

    private static final Tokens TOKENS_NOVOS = new Tokens("access-novo", "refresh-novo", 300, 2592000L);

    private final RepositorioSessoesAparelho sessoes = mock(RepositorioSessoesAparelho.class);
    private final RepositorioPerfis perfis = mock(RepositorioPerfis.class);
    private final ProvedorIdentidade provedor = mock(ProvedorIdentidade.class);
    private final RenovarSessaoAparelho casoDeUso = new RenovarSessaoAparelho(sessoes, perfis, provedor);

    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final Logger logDoCasoDeUso = (Logger) LoggerFactory.getLogger(RenovarSessaoAparelho.class);

    @BeforeEach
    void capturarLog() {
        log.start();
        logDoCasoDeUso.addAppender(log);
    }

    @AfterEach
    void soltarLog() {
        logDoCasoDeUso.detachAppender(log);
    }

    @Test
    void renovaQuandoSessaoEstaAtivaETokenPertenceAoPerfil() {
        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", null, Instant.now());
        SessaoAparelho sessao = SessaoAparelho.nova(perfil.id(), "aparelho-1", Plataforma.ANDROID, "sub-ana",
                Instant.now());
        when(sessoes.buscarPorId(sessao.id())).thenReturn(Optional.of(sessao));
        when(perfis.buscarPorId(perfil.id())).thenReturn(Optional.of(perfil));
        when(provedor.renovarTokens("refresh-atual")).thenReturn(new TokensRenovados("sub-idoso", TOKENS_NOVOS));

        Tokens resultado = casoDeUso.executar(new RenovarSessaoAparelho.Comando(sessao.id(), "refresh-atual"));

        assertThat(resultado).isEqualTo(TOKENS_NOVOS);
    }

    @Test
    void sessaoInexistenteLancaSessaoNaoEncontrada() {
        UUID sessaoId = UUID.randomUUID();
        when(sessoes.buscarPorId(sessaoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(new RenovarSessaoAparelho.Comando(sessaoId, "refresh-atual")))
                .isInstanceOf(SessaoNaoEncontrada.class);
        verifyNoInteractions(perfis, provedor);
    }

    @Test
    void sessaoRevogadaLancaRenovacaoNaoAutorizadaSemChamarKeycloak() {
        UUID perfilId = UUID.randomUUID();
        SessaoAparelho revogada = new SessaoAparelho(UUID.randomUUID(), perfilId, "aparelho-1", Plataforma.IOS,
                "sub-ana", Instant.now().minusSeconds(60), Instant.now());
        when(sessoes.buscarPorId(revogada.id())).thenReturn(Optional.of(revogada));

        assertThatThrownBy(() -> casoDeUso.executar(new RenovarSessaoAparelho.Comando(revogada.id(), "refresh-atual")))
                .isInstanceOf(RenovacaoNaoAutorizada.class);
        verify(provedor, never()).renovarTokens(any());
    }

    @Test
    void refreshTokenDeOutroIdosoLancaRenovacaoNaoAutorizadaSemVazarTokenEmLog() {
        Perfil perfil = Perfil.novoIdoso("sub-idoso-a", "Maria", null, Instant.now());
        SessaoAparelho sessao = SessaoAparelho.nova(perfil.id(), "aparelho-1", Plataforma.ANDROID, "sub-ana",
                Instant.now());
        when(sessoes.buscarPorId(sessao.id())).thenReturn(Optional.of(sessao));
        when(perfis.buscarPorId(perfil.id())).thenReturn(Optional.of(perfil));
        when(provedor.renovarTokens("refresh-secreto-do-b"))
                .thenReturn(new TokensRenovados("sub-idoso-b", TOKENS_NOVOS));

        assertThatThrownBy(() -> casoDeUso.executar(
                new RenovarSessaoAparelho.Comando(sessao.id(), "refresh-secreto-do-b")))
                .isInstanceOf(RenovacaoNaoAutorizada.class)
                .hasMessageNotContaining("refresh-secreto-do-b")
                .hasMessageNotContaining("access-novo")
                .hasMessageNotContaining("refresh-novo");

        assertThat(log.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("refresh-secreto-do-b")
                        || msg.contains("access-novo")
                        || msg.contains("refresh-novo"));
    }

    @Test
    void keycloakIndisponivelPropagaExcecao() {
        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", null, Instant.now());
        SessaoAparelho sessao = SessaoAparelho.nova(perfil.id(), "aparelho-1", Plataforma.ANDROID, "sub-ana",
                Instant.now());
        when(sessoes.buscarPorId(sessao.id())).thenReturn(Optional.of(sessao));
        when(perfis.buscarPorId(perfil.id())).thenReturn(Optional.of(perfil));
        when(provedor.renovarTokens("refresh-atual")).thenThrow(new IdentidadeIndisponivel("Keycloak fora", null));

        assertThatThrownBy(() -> casoDeUso.executar(new RenovarSessaoAparelho.Comando(sessao.id(), "refresh-atual")))
                .isInstanceOf(IdentidadeIndisponivel.class);
    }
}
