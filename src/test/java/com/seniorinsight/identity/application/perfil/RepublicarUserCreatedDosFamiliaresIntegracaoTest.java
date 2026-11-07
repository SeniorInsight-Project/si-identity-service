package com.seniorinsight.identity.application.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Backfill contra o PostgreSQL real: só os perfis FAMILIAR voltam para a outbox. */
@TesteIntegracao
class RepublicarUserCreatedDosFamiliaresIntegracaoTest {

    @Autowired
    RepublicarUserCreatedDosFamiliares casoDeUso;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void gravaUserCreatedSoDosFamiliares() {
        UUID familiar = inserir("FAMILIAR", "ana@example.com");
        UUID idoso = inserir("IDOSO", null);

        casoDeUso.executar(1);

        assertThat(eventos(familiar)).isEqualTo(1);
        assertThat(eventos(idoso)).isZero();
    }

    private UUID inserir(String tipo, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO perfil (id, keycloak_sub, tipo, nome, email, criado_em, atualizado_em)
                VALUES (?, ?, ?, 'Teste', ?, now(), now())
                """, id, UUID.randomUUID().toString(), tipo, email);
        return id;
    }

    private int eventos(UUID perfilId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'user.created' AND agregado_id = ?",
                Integer.class, perfilId);
    }
}
