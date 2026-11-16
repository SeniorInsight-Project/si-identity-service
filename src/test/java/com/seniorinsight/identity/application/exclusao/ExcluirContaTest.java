package com.seniorinsight.identity.application.exclusao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.application.porta.saida.RepositorioTitularesExcluidos;
import com.seniorinsight.identity.domain.evento.ExclusaoConcluida;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.exclusao.SituacaoPedido;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

class ExcluirContaTest {

    private static final String SUB = "7d1c4a52-0b8e-4f0a-9a52-3c1f5d2e9b10";
    private static final UUID PROTOCOLO = UUID.fromString("44b69553-caa4-48a5-9e4b-22da0c4f2090");

    private final RepositorioPedidosExclusao pedidos = mock(RepositorioPedidosExclusao.class);
    private final ProvedorIdentidade provedor = mock(ProvedorIdentidade.class);
    private final RepositorioTitularesExcluidos excluidos = mock(RepositorioTitularesExcluidos.class);
    private final RepositorioPerfis perfis = mock(RepositorioPerfis.class);
    private final RepositorioSessoesAparelho sessoes = mock(RepositorioSessoesAparelho.class);
    private final RepositorioCodigosPareamento codigos = mock(RepositorioCodigosPareamento.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final ExcluirConta casoDeUso = new ExcluirConta(pedidos, provedor, excluidos, perfis, sessoes, codigos,
            publicador, TransactionOperations.withoutTransaction());

    @BeforeEach
    void pedidoNovo() {
        when(pedidos.registrarPendente(any())).thenReturn(SituacaoPedido.PENDENTE);
        when(pedidos.concluir(PROTOCOLO)).thenReturn(true);
    }

    @Test
    void familiarTemOUsuarioApagadoEPerfilSessoesECodigosSomemEAConfirmacaoSaiUmaVez() {
        Perfil perfil = perfil(TipoPerfil.FAMILIAR);
        when(perfis.buscarPorKeycloakSub(SUB)).thenReturn(Optional.of(perfil));

        casoDeUso.executar(pedido(TipoPerfil.FAMILIAR));

        InOrder ordem = inOrder(pedidos, provedor, excluidos, sessoes, perfis, codigos, publicador);
        ordem.verify(pedidos).registrarPendente(pedido(TipoPerfil.FAMILIAR));
        ordem.verify(provedor).excluirUsuario(SUB);
        ordem.verify(pedidos).concluir(PROTOCOLO);
        ordem.verify(excluidos).registrarExclusao(SUB);
        ordem.verify(sessoes).excluirDoPerfil(perfil.id());
        ordem.verify(perfis).excluir(perfil.id());
        ordem.verify(codigos).excluirDoFamiliar(SUB);
        ordem.verify(publicador).publicar(ExclusaoConcluida.de(PROTOCOLO));
        verify(publicador).publicar(any());
    }

    @Test
    void idosoTemOUsuarioApagadoEPerfilESessoesSomemSemMexerNosCodigos() {
        Perfil perfil = perfil(TipoPerfil.IDOSO);
        when(perfis.buscarPorKeycloakSub(SUB)).thenReturn(Optional.of(perfil));

        casoDeUso.executar(pedido(TipoPerfil.IDOSO));

        verify(provedor).excluirUsuario(SUB);
        verify(sessoes).excluirDoPerfil(perfil.id());
        verify(perfis).excluir(perfil.id());
        verify(codigos, never()).excluirDoFamiliar(any());
        verify(publicador).publicar(ExclusaoConcluida.de(PROTOCOLO));
    }

    @Test
    void semPerfilAindaApagaOUsuarioNoKeycloakEConfirma() {
        when(perfis.buscarPorKeycloakSub(SUB)).thenReturn(Optional.empty());

        casoDeUso.executar(pedido(TipoPerfil.FAMILIAR));

        verify(provedor).excluirUsuario(SUB);
        verify(excluidos).registrarExclusao(SUB);
        verify(codigos).excluirDoFamiliar(SUB);
        verify(perfis, never()).excluir(any());
        verifyNoInteractions(sessoes);
        verify(publicador).publicar(ExclusaoConcluida.de(PROTOCOLO));
    }

    @Test
    void keycloakForaDoArNaoApagaNadaNemConfirmaEOPedidoFicaRegistrado() {
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).when(provedor).excluirUsuario(SUB);

        assertThatThrownBy(() -> casoDeUso.executar(pedido(TipoPerfil.FAMILIAR)))
                .isInstanceOf(IdentidadeIndisponivel.class);

        verify(pedidos).registrarPendente(pedido(TipoPerfil.FAMILIAR));
        verify(pedidos, never()).concluir(any());
        verifyNoInteractions(excluidos, sessoes, codigos, publicador);
        verify(perfis, never()).excluir(any());
    }

    @Test
    void quandoOKeycloakVoltaOMesmoPedidoTerminaEConfirmaUmaVez() {
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).doNothing().when(provedor).excluirUsuario(SUB);
        when(perfis.buscarPorKeycloakSub(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(pedido(TipoPerfil.IDOSO)))
                .isInstanceOf(IdentidadeIndisponivel.class);
        casoDeUso.executar(pedido(TipoPerfil.IDOSO));

        verify(publicador).publicar(ExclusaoConcluida.de(PROTOCOLO));
    }

    @Test
    void reentregaDoProtocoloJaConcluidoNaoChamaOKeycloakNemPublicaDeNovo() {
        when(pedidos.registrarPendente(any())).thenReturn(SituacaoPedido.CONCLUIDO);

        casoDeUso.executar(pedido(TipoPerfil.FAMILIAR));

        verifyNoInteractions(provedor, excluidos, perfis, sessoes, codigos, publicador);
        verify(pedidos, never()).concluir(any());
    }

    @Test
    void entregaConcorrenteQuePerdeuACorridaNaoApagaNemPublicaDeNovo() {
        when(pedidos.concluir(PROTOCOLO)).thenReturn(false);

        casoDeUso.executar(pedido(TipoPerfil.FAMILIAR));

        verify(provedor).excluirUsuario(SUB);
        verifyNoInteractions(excluidos, sessoes, codigos, publicador);
        verify(perfis, never()).excluir(any());
    }

    private static PedidoExclusao pedido(TipoPerfil tipo) {
        return new PedidoExclusao(PROTOCOLO, tipo, SUB);
    }

    private static Perfil perfil(TipoPerfil tipo) {
        Instant criadoEm = Instant.parse("2026-10-31T12:00:00Z");
        return new Perfil(UUID.randomUUID(), SUB, tipo, "Ana Souza", null, null, criadoEm, criadoEm);
    }
}
