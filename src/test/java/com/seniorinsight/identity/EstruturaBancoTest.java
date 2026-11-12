package com.seniorinsight.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** O contexto subir já prova que o Hibernate validou o mapeamento ({@code ddl-auto: validate}) contra as migrações. */
@TesteIntegracao
class EstruturaBancoTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAplicaAsMigracoesNoSchemaIdentity() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'identity'", String.class))
                .contains("perfil", "sessao_aparelho", "outbox_evento", "flyway_schema_history");
    }

    @Test
    void outboxTemIndiceParcialDosPendentes() {
        String definicao = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'identity' AND indexname = ?",
                String.class, "ix_outbox_evento_pendentes");

        assertThat(definicao).contains("(criado_em)").contains("WHERE (publicado_em IS NULL)");
    }

    @Test
    void sessaoAparelhoTemUmaSessaoAtivaPorPerfilEAparelho() {
        String definicao = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'identity' AND indexname = ?",
                String.class, "uk_sessao_aparelho_ativa");

        assertThat(definicao).startsWith("CREATE UNIQUE INDEX")
                .contains("(perfil_id, identificador_aparelho)")
                .contains("WHERE (revogado_em IS NULL)");
    }

    @Test
    void perfilTemIdosoIdOpcionalComIndiceUnicoParcial() {
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                 WHERE table_schema = 'identity' AND table_name = 'perfil' AND column_name = 'idoso_id'""",
                String.class)).isEqualTo("YES");

        String definicao = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'identity' AND indexname = ?",
                String.class, "uk_perfil_idoso_id");

        assertThat(definicao).startsWith("CREATE UNIQUE INDEX")
                .contains("(idoso_id)")
                .contains("WHERE (idoso_id IS NOT NULL)");
    }
}
