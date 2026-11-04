package com.seniorinsight.identity.adapters.out.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Linha da tabela {@code outbox_evento}: evento gravado na transação do caso de uso, aguardando publicação.
 *
 * <p>O id é gerado em Java porque também vai no envelope CloudEvents do payload. {@link Persistable} faz o
 * {@code save} inserir direto, sem o SELECT que o Spring Data faria para uma entidade com id preenchido.
 */
@Entity
@Table(name = "outbox_evento")
class OutboxEvento implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean novo = true;

    private String tipoAgregado;

    private UUID agregadoId;

    private String tipoEvento;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant criadoEm;

    private Instant publicadoEm;

    private int tentativas;

    protected OutboxEvento() {
    }

    OutboxEvento(UUID id, String tipoAgregado, UUID agregadoId, String tipoEvento, String payload,
                 Instant criadoEm) {
        this.id = id;
        this.tipoAgregado = tipoAgregado;
        this.agregadoId = agregadoId;
        this.tipoEvento = tipoEvento;
        this.payload = payload;
        this.criadoEm = criadoEm;
    }

    void marcarPublicado(Instant instante) {
        this.publicadoEm = instante;
    }

    void registrarFalha() {
        this.tentativas++;
    }

    @PostLoad
    @PostPersist
    void marcarExistente() {
        this.novo = false;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @Override
    public UUID getId() {
        return id;
    }

    String getTipoAgregado() {
        return tipoAgregado;
    }

    UUID getAgregadoId() {
        return agregadoId;
    }

    String getTipoEvento() {
        return tipoEvento;
    }

    String getPayload() {
        return payload;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getPublicadoEm() {
        return publicadoEm;
    }

    int getTentativas() {
        return tentativas;
    }
}
