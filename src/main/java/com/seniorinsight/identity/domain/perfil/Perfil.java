package com.seniorinsight.identity.domain.perfil;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Perfil de um usuário do SeniorInsight, ligado à conta do Keycloak pelo claim {@code sub}. */
public final class Perfil {

    private final UUID id;
    private final String keycloakSub;
    private final TipoPerfil tipo;
    private final String nome;
    private final String email;
    private final Instant criadoEm;
    private final Instant atualizadoEm;

    public Perfil(UUID id, String keycloakSub, TipoPerfil tipo, String nome, String email, Instant criadoEm,
                  Instant atualizadoEm) {
        this.id = Objects.requireNonNull(id, "id");
        this.keycloakSub = Objects.requireNonNull(keycloakSub, "keycloakSub");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.nome = Objects.requireNonNull(nome, "nome");
        this.email = email;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm");
    }

    /** Novo perfil de familiar, criado no primeiro acesso de quem se cadastrou no Keycloak. */
    public static Perfil novoFamiliar(String keycloakSub, String nome, String email, Instant agora) {
        return new Perfil(UUID.randomUUID(), keycloakSub, TipoPerfil.FAMILIAR, nome, email, agora, agora);
    }

    public UUID id() {
        return id;
    }

    public String keycloakSub() {
        return keycloakSub;
    }

    public TipoPerfil tipo() {
        return tipo;
    }

    public String nome() {
        return nome;
    }

    public String email() {
        return email;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}
