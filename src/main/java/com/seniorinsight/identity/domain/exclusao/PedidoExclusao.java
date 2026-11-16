package com.seniorinsight.identity.domain.exclusao;

import java.util.Objects;
import java.util.UUID;

import com.seniorinsight.identity.domain.perfil.TipoPerfil;

/**
 * Pedido do report-service (HU-022) para apagar a conta de um titular.
 *
 * @param protocolo   identifica o pedido; é o {@code subject} do evento e da confirmação
 * @param tipoTitular IDOSO ou FAMILIAR, como informado no pedido
 * @param titularSub  claim {@code sub} do titular no Keycloak, que é como o perfil é localizado
 */
public record PedidoExclusao(UUID protocolo, TipoPerfil tipoTitular, String titularSub) {

    public PedidoExclusao {
        Objects.requireNonNull(protocolo, "protocolo");
        Objects.requireNonNull(tipoTitular, "tipoTitular");
        if (titularSub == null || titularSub.isBlank()) {
            throw new IllegalArgumentException("titularSub é obrigatório");
        }
    }
}
