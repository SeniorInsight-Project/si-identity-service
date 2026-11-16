package com.seniorinsight.identity.adapters.out.persistencia;

import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.exclusao.SituacaoPedido;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tabela {@code exclusao_pedido}. */
@Component
class RepositorioPedidosExclusaoJdbc implements RepositorioPedidosExclusao {

    private final JdbcClient jdbc;

    RepositorioPedidosExclusaoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SituacaoPedido registrarPendente(PedidoExclusao pedido) {
        jdbc.sql("""
                        INSERT INTO exclusao_pedido (protocolo, tipo_titular, titular_sub, situacao)
                        VALUES (:protocolo, :tipo, :sub, :situacao)
                        ON CONFLICT (protocolo) DO NOTHING
                        """)
                .param("protocolo", pedido.protocolo())
                .param("tipo", pedido.tipoTitular().name())
                .param("sub", pedido.titularSub())
                .param("situacao", SituacaoPedido.PENDENTE.name())
                .update();
        return jdbc.sql("SELECT situacao FROM exclusao_pedido WHERE protocolo = :protocolo")
                .param("protocolo", pedido.protocolo())
                .query(SituacaoPedido.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean concluir(UUID protocolo) {
        return jdbc.sql("""
                        UPDATE exclusao_pedido
                           SET situacao = :concluido, titular_sub = NULL, concluido_em = now()
                         WHERE protocolo = :protocolo AND situacao = :pendente
                        """)
                .param("concluido", SituacaoPedido.CONCLUIDO.name())
                .param("pendente", SituacaoPedido.PENDENTE.name())
                .param("protocolo", protocolo)
                .update() == 1;
    }
}
