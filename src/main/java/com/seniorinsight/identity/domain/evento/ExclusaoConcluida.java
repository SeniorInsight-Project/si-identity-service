package com.seniorinsight.identity.domain.evento;

import java.util.UUID;

/**
 * Este serviço terminou a exclusão do protocolo. Os componentes formam o {@code data} de
 * {@code events/lgpd.erasure.completed.v1.json} do si-contracts, que não leva identificador do titular.
 */
public record ExclusaoConcluida(UUID protocolo, String servico) implements EventoDominio {

    static final String SERVICO = "identity-service";

    public static ExclusaoConcluida de(UUID protocolo) {
        return new ExclusaoConcluida(protocolo, SERVICO);
    }

    @Override
    public String tipoAgregado() {
        return "pedido_exclusao";
    }

    @Override
    public UUID agregadoId() {
        return protocolo;
    }

    @Override
    public String tipoEvento() {
        return "lgpd.erasure.completed";
    }
}
