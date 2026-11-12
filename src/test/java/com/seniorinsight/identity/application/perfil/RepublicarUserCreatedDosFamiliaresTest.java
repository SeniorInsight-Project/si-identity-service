package com.seniorinsight.identity.application.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioTitularesExcluidos;
import com.seniorinsight.identity.domain.evento.UserCreated;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class RepublicarUserCreatedDosFamiliaresTest {

    private final RepositorioPerfis repositorio = mock(RepositorioPerfis.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final TransactionTemplate transacao = mock(TransactionTemplate.class);
    private final RepositorioTitularesExcluidos excluidos = mock(RepositorioTitularesExcluidos.class);
    private final RepublicarUserCreatedDosFamiliares casoDeUso =
            new RepublicarUserCreatedDosFamiliares(repositorio, publicador, excluidos, transacao);

    RepublicarUserCreatedDosFamiliaresTest() {
        when(transacao.execute(any())).thenAnswer(i -> i.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void publicaUmUserCreatedPorFamiliarPercorrendoTodosOsLotes() {
        Perfil a = familiar("sub-a");
        Perfil b = familiar("sub-b");
        Perfil c = familiar("sub-c");
        when(repositorio.listarPorTipoApos(eq(TipoPerfil.FAMILIAR), any(), eq(2)))
                .thenReturn(List.of(a, b))
                .thenReturn(List.of(c))
                .thenReturn(List.of());

        int total = casoDeUso.executar(2);

        assertThat(total).isEqualTo(3);
        ArgumentCaptor<UserCreated> eventos = ArgumentCaptor.forClass(UserCreated.class);
        verify(publicador, org.mockito.Mockito.times(3)).publicar(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(UserCreated::keycloakSub)
                .containsExactly("sub-a", "sub-b", "sub-c");
        // O segundo lote continua depois do último id do primeiro.
        verify(repositorio).listarPorTipoApos(TipoPerfil.FAMILIAR, b.id(), 2);
    }

    @Test
    void naoRepublicaNadaDeTitularExcluidoEContinuaOsLotesDepoisDele() {
        Perfil a = familiar("sub-a");
        Perfil excluido = familiar("sub-excluido");
        Perfil c = familiar("sub-c");
        when(excluidos.bloquearEVerificarExclusao("sub-excluido")).thenReturn(true);
        when(repositorio.listarPorTipoApos(eq(TipoPerfil.FAMILIAR), any(), eq(2)))
                .thenReturn(List.of(a, excluido))
                .thenReturn(List.of(c))
                .thenReturn(List.of());

        int total = casoDeUso.executar(2);

        assertThat(total).isEqualTo(2);
        ArgumentCaptor<UserCreated> eventos = ArgumentCaptor.forClass(UserCreated.class);
        verify(publicador, org.mockito.Mockito.times(2)).publicar(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(UserCreated::keycloakSub).containsExactly("sub-a", "sub-c");
        verify(repositorio).listarPorTipoApos(TipoPerfil.FAMILIAR, excluido.id(), 2);
    }

    @Test
    void semFamiliaresNaoPublicaNada() {
        when(repositorio.listarPorTipoApos(any(), any(), eq(10))).thenReturn(List.of());

        assertThat(casoDeUso.executar(10)).isZero();
    }

    @Test
    void loteInvalidoEhRecusado() {
        assertThatThrownBy(() -> casoDeUso.executar(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Perfil familiar(String sub) {
        return Perfil.novoFamiliar(sub, "Nome " + sub, sub + "@exemplo.com", Instant.parse("2026-10-31T12:00:00Z"));
    }
}
