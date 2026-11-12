package com.seniorinsight.identity.adapters.out.persistencia;

import com.seniorinsight.identity.application.porta.saida.RepositorioTitularesExcluidos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tabela {@code titular_excluido}: só o sha256 do {@code sub}. O travamento é um advisory lock da transação. */
@Component
class RepositorioTitularesExcluidosJdbc implements RepositorioTitularesExcluidos {

    private static final int SEMENTE_DO_LOCK = 0;

    private final JdbcClient jdbc;

    RepositorioTitularesExcluidosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarExclusao(String keycloakSub) {
        bloquear(keycloakSub);
        jdbc.sql("""
                        INSERT INTO titular_excluido (chave) VALUES (sha256(convert_to(:sub, 'UTF8')))
                        ON CONFLICT DO NOTHING
                        """)
                .param("sub", keycloakSub)
                .update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean bloquearEVerificarExclusao(String keycloakSub) {
        bloquear(keycloakSub);
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM titular_excluido WHERE chave = sha256(convert_to(:sub, 'UTF8')))
                        """)
                .param("sub", keycloakSub)
                .query(Boolean.class)
                .single();
    }

    private void bloquear(String keycloakSub) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:sub, :semente))")
                .param("sub", keycloakSub)
                .param("semente", SEMENTE_DO_LOCK)
                .query()
                .listOfRows();
    }
}
