package com.seniorinsight.identity.application.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AtualizarMeuPerfilTest {

    private static final String SUB = "f1e2d3c4-b5a6-4789-8abc-def012345678";

    private final RepositorioPerfis repositorio = mock(RepositorioPerfis.class);
    private final ProvedorIdentidade provedorIdentidade = mock(ProvedorIdentidade.class);
    private final AtualizarMeuPerfil casoDeUso = new AtualizarMeuPerfil(repositorio, provedorIdentidade);

    @Test
    void gravaONomeNoPerfilENoKeycloakEDevolveOPerfilAtualizado() {
        Perfil existente = perfil();
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.of(existente));

        Perfil atualizado = casoDeUso.executar(new AtualizarMeuPerfil.Comando(SUB, "Ana Souza Lima"));

        assertThat(atualizado.nome()).isEqualTo("Ana Souza Lima");
        assertThat(atualizado.id()).isEqualTo(existente.id());
        assertThat(atualizado.email()).isEqualTo(existente.email());
        assertThat(atualizado.tipo()).isEqualTo(existente.tipo());
        assertThat(atualizado.criadoEm()).isEqualTo(existente.criadoEm());
        assertThat(atualizado.atualizadoEm()).isAfter(existente.atualizadoEm());

        ArgumentCaptor<Perfil> gravado = ArgumentCaptor.forClass(Perfil.class);
        var ordem = inOrder(repositorio, provedorIdentidade);
        ordem.verify(repositorio).atualizarNome(gravado.capture());
        ordem.verify(provedorIdentidade).atualizarNome(SUB, "Ana Souza Lima");
        assertThat(gravado.getValue()).isSameAs(atualizado);
    }

    @Test
    void removeEspacosNasPontasDoNome() {
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.of(perfil()));

        Perfil atualizado = casoDeUso.executar(new AtualizarMeuPerfil.Comando(SUB, "  Ana Lima  "));

        assertThat(atualizado.nome()).isEqualTo("Ana Lima");
        verify(provedorIdentidade).atualizarNome(SUB, "Ana Lima");
    }

    @Test
    void nomeDeUmaPalavraSoLancaNomeInvalidoSemGravarNada() {
        assertThatThrownBy(() -> casoDeUso.executar(new AtualizarMeuPerfil.Comando(SUB, " Ana ")))
                .isInstanceOf(NomeInvalido.class);

        verify(repositorio, never()).atualizarNome(any());
        verify(provedorIdentidade, never()).atualizarNome(any(), any());
    }

    @Test
    void semPerfilLancaPerfilNaoEncontradoSemTocarNoKeycloak() {
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(new AtualizarMeuPerfil.Comando(SUB, "Ana Lima")))
                .isInstanceOf(PerfilNaoEncontrado.class);

        verify(repositorio, never()).atualizarNome(any());
        verify(provedorIdentidade, never()).atualizarNome(any(), any());
    }

    @Test
    void falhaNoKeycloakPropagaParaDesfazerATransacao() {
        when(repositorio.buscarPorKeycloakSub(SUB)).thenReturn(Optional.of(perfil()));
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).when(provedorIdentidade).atualizarNome(SUB, "Ana Lima");

        assertThatThrownBy(() -> casoDeUso.executar(new AtualizarMeuPerfil.Comando(SUB, "Ana Lima")))
                .isInstanceOf(IdentidadeIndisponivel.class);
    }

    private static Perfil perfil() {
        Instant criadoEm = Instant.parse("2026-10-31T12:00:00Z");
        return new Perfil(UUID.randomUUID(), SUB, TipoPerfil.FAMILIAR, "Ana Souza", "ana@example.com", null,
                criadoEm, criadoEm);
    }
}
