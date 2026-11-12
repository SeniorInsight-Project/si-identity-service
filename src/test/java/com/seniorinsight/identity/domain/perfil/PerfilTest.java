package com.seniorinsight.identity.domain.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PerfilTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T12:00:00Z");

    @Test
    void novoIdosoGuardaOIdosoIdDoCare() {
        UUID idosoId = UUID.randomUUID();

        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", idosoId, AGORA);

        assertThat(perfil.tipo()).isEqualTo(TipoPerfil.IDOSO);
        assertThat(perfil.idosoId()).isEqualTo(idosoId);
    }

    @Test
    void novoIdosoAceitaIdosoIdNulo() {
        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", null, AGORA);

        assertThat(perfil.idosoId()).isNull();
    }

    @Test
    void novoFamiliarNaoTemIdosoId() {
        Perfil perfil = Perfil.novoFamiliar("sub-ana", "Ana", "ana@example.com", AGORA);

        assertThat(perfil.idosoId()).isNull();
    }

    @Test
    void familiarNaoPodeTerIdosoId() {
        assertThatThrownBy(() -> new Perfil(UUID.randomUUID(), "sub-ana", TipoPerfil.FAMILIAR, "Ana",
                "ana@example.com", UUID.randomUUID(), AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comNomeMantemOIdosoId() {
        UUID idosoId = UUID.randomUUID();
        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", idosoId, AGORA);

        assertThat(perfil.comNome("Maria Aparecida", AGORA.plusSeconds(60)).idosoId()).isEqualTo(idosoId);
    }
}
