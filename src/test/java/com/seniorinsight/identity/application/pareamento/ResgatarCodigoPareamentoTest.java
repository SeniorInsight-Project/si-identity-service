package com.seniorinsight.identity.application.pareamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class ResgatarCodigoPareamentoTest {

    private static final Duration RETENCAO = Duration.ofHours(1);
    private static final Tokens TOKENS = new Tokens("access", "refresh", 300, 1800L);

    private final RepositorioCodigosPareamento codigos = mock(RepositorioCodigosPareamento.class);
    private final ProvedorIdentidade provedor = mock(ProvedorIdentidade.class);
    private final RepositorioPerfis perfis = mock(RepositorioPerfis.class);
    private final RepositorioSessoesAparelho sessoes = mock(RepositorioSessoesAparelho.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final ResgatarCodigoPareamento casoDeUso = new ResgatarCodigoPareamento(codigos, provedor, perfis,
            sessoes, publicador, TransactionOperations.withoutTransaction(),
            new PropriedadesPareamento(Duration.ofHours(24), RETENCAO));

    private final CodigoPareamento codigo = new CodigoPareamento("K7P2QX", "sub-ana", "Maria", null,
            Instant.now().plus(Duration.ofHours(1)));
    private final ResgatarCodigoPareamento.Comando comando =
            new ResgatarCodigoPareamento.Comando("K7P2QX", "aparelho-1", Plataforma.IOS);

    @Test
    void primeiroResgateCriaPerfilIdosoSemEmailPublicaUserCreatedERegistraASessao() {
        when(codigos.consumir(eq("K7P2QX"), any(), eq(RETENCAO))).thenReturn(new Consumo.Resgatado(codigo));
        when(provedor.obterOuCriarUsuarioIdoso(any(), eq("Maria"))).thenReturn("sub-idoso");
        when(perfis.buscarPorKeycloakSub("sub-idoso")).thenReturn(Optional.empty());
        when(perfis.inserirSeAusente(any())).thenReturn(true);
        when(provedor.emitirTokens("sub-idoso")).thenReturn(TOKENS);

        ResgatarCodigoPareamento.Resultado resultado = casoDeUso.executar(comando);

        Perfil perfil = resultado.perfil();
        assertThat(perfil.tipo()).isEqualTo(TipoPerfil.IDOSO);
        assertThat(perfil.email()).isNull();
        assertThat(perfil.keycloakSub()).isEqualTo("sub-idoso");
        assertThat(resultado.tokens()).isEqualTo(TOKENS);
        verify(publicador).publicar(UserCreated.de(perfil));
        verify(sessoes).registrar(argThat(sessao ->
                sessao.id().equals(resultado.sessaoAparelhoId())
                        && sessao.perfilId().equals(perfil.id())
                        && sessao.plataforma() == Plataforma.IOS
                        && sessao.pareadoPor().equals("sub-ana")));
        verify(codigos, never()).devolver(any(), any());
        assertThat(resultado.idosoId()).isNull();
    }

    @Test
    void codigoComIdosoIdDevolveOIdosoIdNoResultado() {
        UUID idosoId = UUID.randomUUID();
        CodigoPareamento comIdoso = new CodigoPareamento("K7P2QX", "sub-ana", "Maria", idosoId, codigo.expiraEm());
        when(codigos.consumir(anyString(), any(), any())).thenReturn(new Consumo.Resgatado(comIdoso));
        when(provedor.obterOuCriarUsuarioIdoso(eq(idosoId), eq("Maria"))).thenReturn("sub-idoso");
        when(perfis.buscarPorKeycloakSub("sub-idoso")).thenReturn(Optional.empty());
        when(perfis.inserirSeAusente(any())).thenReturn(true);
        when(provedor.emitirTokens("sub-idoso")).thenReturn(TOKENS);

        assertThat(casoDeUso.executar(comando).idosoId()).isEqualTo(idosoId);
    }

    @Test
    void perfilExistenteNaoPublicaOutroEvento() {
        Perfil existente = Perfil.novoIdoso("sub-idoso", "Maria", Instant.now());
        when(codigos.consumir(anyString(), any(), any())).thenReturn(new Consumo.Resgatado(codigo));
        when(provedor.obterOuCriarUsuarioIdoso(any(), any())).thenReturn("sub-idoso");
        when(perfis.buscarPorKeycloakSub("sub-idoso")).thenReturn(Optional.of(existente));
        when(provedor.emitirTokens("sub-idoso")).thenReturn(TOKENS);

        assertThat(casoDeUso.executar(comando).perfil()).isSameAs(existente);
        verify(perfis, never()).inserirSeAusente(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void codigoJaResgatadoExpiradoOuInexistente() {
        when(codigos.consumir(anyString(), any(), any())).thenReturn(new Consumo.JaResgatado());
        assertThatThrownBy(() -> casoDeUso.executar(comando)).isInstanceOf(CodigoJaResgatado.class);

        when(codigos.consumir(anyString(), any(), any())).thenReturn(new Consumo.Expirado());
        assertThatThrownBy(() -> casoDeUso.executar(comando)).isInstanceOf(CodigoExpirado.class);

        when(codigos.consumir(anyString(), any(), any())).thenReturn(new Consumo.Inexistente());
        assertThatThrownBy(() -> casoDeUso.executar(comando)).isInstanceOf(CodigoNaoEncontrado.class);

        verifyNoInteractions(provedor, sessoes, publicador);
    }

    @Test
    void falhaNoTokenExchangeDevolveOCodigoParaNovaTentativa() {
        when(codigos.consumir(anyString(), any(), any())).thenReturn(new Consumo.Resgatado(codigo));
        when(provedor.obterOuCriarUsuarioIdoso(any(), any())).thenReturn("sub-idoso");
        when(perfis.buscarPorKeycloakSub("sub-idoso")).thenReturn(Optional.empty());
        when(perfis.inserirSeAusente(any())).thenReturn(true);
        when(provedor.emitirTokens("sub-idoso")).thenThrow(new IdentidadeIndisponivel("fora do ar", null));

        assertThatThrownBy(() -> casoDeUso.executar(comando)).isInstanceOf(IdentidadeIndisponivel.class);

        verify(codigos).devolver(codigo, RETENCAO);
    }

    @Test
    void chaveDoIdosoEhOIdosoIdOuFixaParaOCodigo() {
        UUID idosoId = UUID.randomUUID();
        CodigoPareamento comIdoso = new CodigoPareamento("K7P2QX", "sub-ana", "Maria", idosoId, codigo.expiraEm());

        assertThat(ResgatarCodigoPareamento.chaveDoIdoso(comIdoso)).isEqualTo(idosoId);
        assertThat(ResgatarCodigoPareamento.chaveDoIdoso(codigo))
                .isEqualTo(ResgatarCodigoPareamento.chaveDoIdoso(codigo))
                .isNotEqualTo(ResgatarCodigoPareamento.chaveDoIdoso(
                        new CodigoPareamento("ABCDEF", "sub-ana", "Maria", null, codigo.expiraEm())));
    }
}
