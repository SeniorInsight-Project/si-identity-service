package com.seniorinsight.identity.application.porta.saida;

import java.util.UUID;

import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.exclusao.SituacaoPedido;

/** Pedidos de exclusão recebidos, um por protocolo. Exigem transação. */
public interface RepositorioPedidosExclusao {

    /**
     * Registra o pedido como pendente, com o {@code sub} do titular, se o protocolo ainda não existe.
     *
     * @return a situação do protocolo: {@code PENDENTE} para um pedido novo ou ainda não concluído, {@code CONCLUIDO}
     *         para um já confirmado
     */
    SituacaoPedido registrarPendente(PedidoExclusao pedido);

    /**
     * Marca o pedido como concluído e apaga o {@code sub} do titular da tabela.
     *
     * @return {@code false} se o pedido já estava concluído (outra entrega do mesmo protocolo chegou antes)
     */
    boolean concluir(UUID protocolo);
}
