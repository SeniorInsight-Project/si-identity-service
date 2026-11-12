package com.seniorinsight.identity.domain.perfil;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Perfil de um usuário do SeniorInsight, ligado à conta do Keycloak pelo claim {@code sub}.
 *
 * <p>O {@code idosoId} é o id do idoso no care-service. Só o perfil IDOSO pode tê-lo, e mesmo ele pode não ter:
 * depende de o código de pareamento ter sido gerado com esse id.
 */
public final class Perfil {

    private final UUID id;
    private final String keycloakSub;
    private final TipoPerfil tipo;
    private final String nome;
    private final String email;
    private final UUID idosoId;
    private final Instant criadoEm;
    private final Instant atualizadoEm;

    public Perfil(UUID id, String keycloakSub, TipoPerfil tipo, String nome, String email, UUID idosoId,
                  Instant criadoEm, Instant atualizadoEm) {
        this.id = Objects.requireNonNull(id, "id");
        this.keycloakSub = Objects.requireNonNull(keycloakSub, "keycloakSub");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.nome = Objects.requireNonNull(nome, "nome");
        this.email = email;
        if (idosoId != null && tipo != TipoPerfil.IDOSO) {
            throw new IllegalArgumentException("Só o perfil IDOSO pode ter idosoId");
        }
        this.idosoId = idosoId;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm");
    }

    /** Novo perfil de familiar, criado no primeiro acesso de quem se cadastrou no Keycloak. */
    public static Perfil novoFamiliar(String keycloakSub, String nome, String email, Instant agora) {
        return new Perfil(UUID.randomUUID(), keycloakSub, TipoPerfil.FAMILIAR, nome, email, null, agora, agora);
    }

    /**
     * Novo perfil de idoso, criado no resgate do código de pareamento. O idoso não tem e-mail.
     *
     * @param idosoId id do idoso no care-service, ou null quando o código foi gerado sem ele
     */
    public static Perfil novoIdoso(String keycloakSub, String nome, UUID idosoId, Instant agora) {
        return new Perfil(UUID.randomUUID(), keycloakSub, TipoPerfil.IDOSO, nome, null, idosoId, agora, agora);
    }

    /** Cópia do perfil com o nome novo; só o nome e o {@code atualizadoEm} mudam. */
    public Perfil comNome(String novoNome, Instant agora) {
        return new Perfil(id, keycloakSub, tipo, novoNome, email, idosoId, criadoEm, agora);
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

    public UUID idosoId() {
        return idosoId;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}
