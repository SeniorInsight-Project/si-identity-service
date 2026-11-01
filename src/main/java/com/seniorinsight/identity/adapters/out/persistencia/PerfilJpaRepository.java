package com.seniorinsight.identity.adapters.out.persistencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PerfilJpaRepository extends JpaRepository<PerfilEntidade, UUID> {

    Optional<PerfilEntidade> findByKeycloakSub(String keycloakSub);

    /**
     * {@code ON CONFLICT DO NOTHING} no unique de {@code keycloak_sub}: se outra transação inseriu o mesmo sub, o
     * PostgreSQL espera ela terminar e não insere nada, sem erro e sem abortar a transação atual.
     *
     * <p>O {@code CAST} do e-mail evita que o null seja enviado sem tipo (o PostgreSQL o trataria como bytea).
     *
     * @return linhas inseridas (0 ou 1)
     */
    @Modifying
    @Query(value = """
            INSERT INTO perfil (id, keycloak_sub, tipo, nome, email, criado_em, atualizado_em)
            VALUES (:id, :keycloakSub, :tipo, :nome, CAST(:email AS VARCHAR), :criadoEm, :atualizadoEm)
            ON CONFLICT (keycloak_sub) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(@Param("id") UUID id,
                         @Param("keycloakSub") String keycloakSub,
                         @Param("tipo") String tipo,
                         @Param("nome") String nome,
                         @Param("email") String email,
                         @Param("criadoEm") Instant criadoEm,
                         @Param("atualizadoEm") Instant atualizadoEm);
}
