package com.seniorinsight.identity.domain.exclusao;

/** Em que ponto está um pedido de exclusão neste serviço. */
public enum SituacaoPedido {
    /** Registrado; o usuário no Keycloak e os dados locais ainda podem estar de pé. */
    PENDENTE,
    /** Tudo apagado e a confirmação gravada na outbox. */
    CONCLUIDO
}
