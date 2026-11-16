package com.seniorinsight.identity.adapters.out.persistencia;

import java.sql.Timestamp;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tabela {@code sessao_aparelho}. SQL direto: a sessão só é gravada aqui, nunca lida como entidade. */
@Component
class RepositorioSessoesAparelhoJdbc implements RepositorioSessoesAparelho {

    private final JdbcClient jdbc;

    RepositorioSessoesAparelhoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(SessaoAparelho sessao) {
        jdbc.sql("""
                        UPDATE sessao_aparelho
                           SET revogado_em = :agora
                         WHERE perfil_id = :perfilId
                           AND identificador_aparelho = :identificador
                           AND revogado_em IS NULL
                        """)
                .param("agora", Timestamp.from(sessao.criadoEm()))
                .param("perfilId", sessao.perfilId())
                .param("identificador", sessao.identificadorAparelho())
                .update();
        jdbc.sql("""
                        INSERT INTO sessao_aparelho (id, perfil_id, identificador_aparelho, plataforma, pareado_por,
                                                     criado_em)
                        VALUES (:id, :perfilId, :identificador, :plataforma, :pareadoPor, :criadoEm)
                        """)
                .param("id", sessao.id())
                .param("perfilId", sessao.perfilId())
                .param("identificador", sessao.identificadorAparelho())
                .param("plataforma", sessao.plataforma().name())
                .param("pareadoPor", sessao.pareadoPor())
                .param("criadoEm", Timestamp.from(sessao.criadoEm()))
                .update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void excluirDoPerfil(UUID perfilId) {
        jdbc.sql("DELETE FROM sessao_aparelho WHERE perfil_id = :perfilId")
                .param("perfilId", perfilId)
                .update();
    }
}
