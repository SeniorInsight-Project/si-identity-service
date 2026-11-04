package com.seniorinsight.identity.application.pareamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.RedeDoIdoso;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import org.junit.jupiter.api.Test;

class GerarCodigoPareamentoTest {

    private static final Duration VALIDADE = Duration.ofHours(24);
    private static final Duration RETENCAO = Duration.ofHours(1);

    private final RepositorioCodigosPareamento codigos = mock(RepositorioCodigosPareamento.class);
    private final RedeDoIdoso rede = mock(RedeDoIdoso.class);
    private final GerarCodigoPareamento casoDeUso =
            new GerarCodigoPareamento(codigos, rede, new PropriedadesPareamento(VALIDADE, RETENCAO));

    @Test
    void comIdosoIdSoOAdministradorDaRedeGera() {
        UUID idosoId = UUID.randomUUID();
        when(rede.familiarEhAdministrador(idosoId, "sub-ana", "token-ana")).thenReturn(false);

        assertThatThrownBy(() -> casoDeUso.executar(new GerarCodigoPareamento.Comando("sub-ana", "token-ana", "Maria",
                idosoId))).isInstanceOf(FamiliarSemVinculo.class);
        verify(codigos, never()).salvar(any(), any());
    }

    @Test
    void administradorRecebeCodigoComValidadeConfigurada() {
        UUID idosoId = UUID.randomUUID();
        when(rede.familiarEhAdministrador(idosoId, "sub-ana", "token-ana")).thenReturn(true);
        when(codigos.salvar(any(), any())).thenReturn(true);

        CodigoPareamento codigo = casoDeUso.executar(
                new GerarCodigoPareamento.Comando("sub-ana", "token-ana", "  Maria  ", idosoId));

        assertThat(codigo.familiarSub()).isEqualTo("sub-ana");
        assertThat(codigo.nomeIdoso()).isEqualTo("Maria");
        assertThat(codigo.idosoId()).isEqualTo(idosoId);
        assertThat(codigo.expiraEm()).isBetween(Instant.now().plus(VALIDADE).minusSeconds(5),
                Instant.now().plus(VALIDADE));
        verify(codigos).salvar(codigo, RETENCAO);
    }

    @Test
    void semIdosoIdNaoConsultaOCareService() {
        when(codigos.salvar(any(), any())).thenReturn(true);

        casoDeUso.executar(new GerarCodigoPareamento.Comando("sub-ana", "token-ana", "Maria", null));

        verifyNoInteractions(rede);
    }

    @Test
    void sorteiaDeNovoQuandoOCodigoJaEstaEmUso() {
        when(codigos.salvar(any(), any())).thenReturn(false, false, true);

        casoDeUso.executar(new GerarCodigoPareamento.Comando("sub-ana", "token-ana", "Maria", null));

        verify(codigos, times(3)).salvar(any(), any());
    }

    @Test
    void desisteDepoisDeTodasAsTentativas() {
        when(codigos.salvar(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> casoDeUso.executar(
                new GerarCodigoPareamento.Comando("sub-ana", "token-ana", "Maria", null)))
                .isInstanceOf(IllegalStateException.class);
        verify(codigos, times(GerarCodigoPareamento.TENTATIVAS)).salvar(any(), any());
    }
}
