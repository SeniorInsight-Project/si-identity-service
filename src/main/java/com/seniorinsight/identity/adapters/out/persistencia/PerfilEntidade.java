package com.seniorinsight.identity.adapters.out.persistencia;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Linha da tabela {@code perfil}. A inserção é por SQL nativo ({@link PerfilJpaRepository#inserirSeAusente}). */
@Entity
@Table(name = "perfil")
class PerfilEntidade {

    @Id
    private UUID id;

    private String keycloakSub;

    @Enumerated(EnumType.STRING)
    private TipoPerfil tipo;

    private String nome;

    private String email;

    private Instant criadoEm;

    private Instant atualizadoEm;

    protected PerfilEntidade() {
    }

    Perfil paraDominio() {
        return new Perfil(id, keycloakSub, tipo, nome, email, criadoEm, atualizadoEm);
    }
}
