package com.seniorinsight.identity.domain.evento;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;

/** Perfil criado. Os componentes formam o {@code data} de {@code events/user.created.v1.json} do si-contracts. */
public record UserCreated(UUID perfilId, String keycloakSub, TipoPerfil tipo, String nome, String email,
                          Instant criadoEm) implements EventoDominio {

    public static UserCreated de(Perfil perfil) {
        return new UserCreated(perfil.id(), perfil.keycloakSub(), perfil.tipo(), perfil.nome(), perfil.email(),
                perfil.criadoEm());
    }

    @Override
    public String tipoAgregado() {
        return "perfil";
    }

    @Override
    public UUID agregadoId() {
        return perfilId;
    }

    @Override
    public String tipoEvento() {
        return "user.created";
    }
}
