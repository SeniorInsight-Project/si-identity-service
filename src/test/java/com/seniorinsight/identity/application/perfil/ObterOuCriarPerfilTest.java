package com.seniorinsight.identity.application.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.evento.UserCreated;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ObterOuCriarPerfilTest {

    private static final String SUB = "f1e2d3c4-b5a6-4789-8abc-def012345678";

    private final RepositorioPerfis repositorio = mock(RepositorioPerfis.class);
    private final PublicadorEventos publicador = mock(PublicadorEventos.class);
    private final ObterOuCriarPerfil casoDeUso = new ObterOuCriarPerfil(repositorio, publicador);

    @Test
    void perfilExistenteEhDevolvidoSemPublicarEvento() {
        Perfil existente = perfil(SUB);
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.of(existente));

        Perfil perfil = casoDeUso.executar(familiar("Ana Souza", "ana", "ana@example.com"));

        assertThat(perfil).isSameAs(existente);
        verify(repositorio, never()).inserirSeAusente(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    void primeiroAcessoDoFamiliarCriaOPerfilEPublicaUserCreated() {
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.empty());
        when(repositorio.inserirSeAusente(any())).thenReturn(true);

        Perfil perfil = casoDeUso.executar(familiar("Ana Souza", "ana", "ana@example.com"));

        assertThat(perfil.keycloakSub()).isEqualTo(SUB);
        assertThat(perfil.tipo()).isEqualTo(TipoPerfil.FAMILIAR);
        assertThat(perfil.nome()).isEqualTo("Ana Souza");
        assertThat(perfil.email()).isEqualTo("ana@example.com");
        assertThat(perfil.atualizadoEm()).isEqualTo(perfil.criadoEm());
        verify(repositorio).inserirSeAusente(perfil);

        ArgumentCaptor<UserCreated> evento = ArgumentCaptor.forClass(UserCreated.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new UserCreated(perfil.id(), SUB, TipoPerfil.FAMILIAR, "Ana Souza",
                "ana@example.com", perfil.criadoEm()));
        assertThat(evento.getValue().tipoAgregado()).isEqualTo("perfil");
        assertThat(evento.getValue().agregadoId()).isEqualTo(perfil.id());
        assertThat(evento.getValue().tipoEvento()).isEqualTo("user.created");
    }

    @Test
    void semClaimNameUsaOPreferredUsername() {
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.empty());
        when(repositorio.inserirSeAusente(any())).thenReturn(true);

        Perfil perfil = casoDeUso.executar(familiar(null, "ana", null));

        assertThat(perfil.nome()).isEqualTo("ana");
        assertThat(perfil.email()).isNull();
    }

    @Test
    void semPerfilESemPapelFamiliarLancaPerfilNaoEncontrado() {
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(
                new ObterOuCriarPerfil.Comando(SUB, Set.of("idoso", "offline_access"), "Maria", "maria", null)))
                .isInstanceOf(PerfilNaoEncontrado.class);

        verify(repositorio, never()).inserirSeAusente(any());
        verify(publicador, never()).publicar(any());
    }

    @Test
    void corridaNoUniqueRelePerfilDaOutraRequisicaoSemPublicarDeNovo() {
        Perfil daOutraRequisicao = perfil(SUB);
        when(repositorio.buscarPorKeycloakSub(SUB))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(daOutraRequisicao));
        when(repositorio.inserirSeAusente(any())).thenReturn(false);

        Perfil perfil = casoDeUso.executar(familiar("Ana Souza", "ana", "ana@example.com"));

        assertThat(perfil).isSameAs(daOutraRequisicao);
        verify(publicador, never()).publicar(any());
    }

    private static ObterOuCriarPerfil.Comando familiar(String nome, String nomeUsuario, String email) {
        return new ObterOuCriarPerfil.Comando(SUB, Set.of("familiar", "offline_access"), nome, nomeUsuario, email);
    }

    private static Perfil perfil(String sub) {
        Instant criadoEm = Instant.parse("2026-10-31T12:00:00Z");
        return new Perfil(UUID.randomUUID(), sub, TipoPerfil.FAMILIAR, "Ana Souza", "ana@example.com", criadoEm,
                criadoEm);
    }
}
