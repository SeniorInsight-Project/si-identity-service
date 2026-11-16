package com.seniorinsight.identity.adapters.in.mensageria;

/** O envelope de {@code lgpd.erasure.requested} não confere com o contrato; reentregar não adianta. */
class PedidoExclusaoInvalido extends IllegalArgumentException {

    PedidoExclusaoInvalido(String motivo) {
        super("Pedido de exclusão inválido: " + motivo);
    }
}
