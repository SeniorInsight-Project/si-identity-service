package com.seniorinsight.identity.adapters.out.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PerfilJpaRepository extends JpaRepository<PerfilEntidade, UUID> {

    Optional<PerfilEntidade> findByKeycloakSub(String keycloakSub);

    List<PerfilEntidade> findByTipoAndIdGreaterThanOrderByIdAsc(TipoPerfil tipo, UUID id, Pageable pagina);

    @Modifying
    @Query("DELETE FROM PerfilEntidade p WHERE p.id = :id")
    int excluir(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PerfilEntidade p SET p.nome = :nome, p.atualizadoEm = :atualizadoEm WHERE p.id = :id")
    int atualizarNome(@Param("id") UUID id, @Param("nome") String nome, @Param("atualizadoEm") Instant atualizadoEm);

    /**
     * {@code ON CONFLICT DO NOTHING} no unique de {@code keycloak_sub}: se outra transação inseriu o mesmo sub, o
     * PostgreSQL espera ela terminar e não insere nada, sem erro e sem abortar a transação atual.
     *
     * <p>Os {@code CAST} do e-mail e do idosoId evitam que o null seja enviado sem tipo (o PostgreSQL o trataria como bytea).
     *
     * @return linhas inseridas (0 ou 1)
     */
    @Modifying
    @Query(value = """
            INSERT INTO perfil (id, keycloak_sub, tipo, nome, email, idoso_id, criado_em, atualizado_em)
            VALUES (:id, :keycloakSub, :tipo, :nome, CAST(:email AS VARCHAR), CAST(:idosoId AS UUID), :criadoEm,
                    :atualizadoEm)
            ON CONFLICT (keycloak_sub) DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(@Param("id") UUID id,
                         @Param("keycloakSub") String keycloakSub,
                         @Param("tipo") String tipo,
                         @Param("nome") String nome,
                         @Param("email") String email,
                         @Param("idosoId") UUID idosoId,
                         @Param("criadoEm") Instant criadoEm,
                         @Param("atualizadoEm") Instant atualizadoEm);
}
