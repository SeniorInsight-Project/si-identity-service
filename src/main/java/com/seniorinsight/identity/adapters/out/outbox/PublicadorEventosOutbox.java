package com.seniorinsight.identity.adapters.out.outbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.domain.evento.EventoDominio;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o evento na {@code outbox_evento} dentro da transação de quem chamou; o {@link RelayOutbox} publica depois.
 * Exige transação ativa para que o evento nunca seja gravado sem a mudança de estado que o originou.
 *
 * <p>O payload é o envelope CloudEvents 1.0 em modo estruturado, como exigem os contratos de {@code events/} do
 * si-contracts: {@code id} = id da linha na outbox (também o {@code message_id} da mensagem AMQP), {@code subject} =
 * id do agregado e {@code data} = o próprio evento.
 */
@Component
class PublicadorEventosOutbox implements PublicadorEventos {

    static final String SOURCE = "/identity-service";
    static final String URL_SCHEMAS = "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/";

    private final OutboxEventoRepository repositorio;
    private final ObjectMapper objectMapper;

    PublicadorEventosOutbox(OutboxEventoRepository repositorio, ObjectMapper objectMapper) {
        this.repositorio = repositorio;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publicar(EventoDominio evento) {
        UUID id = UUID.randomUUID();
        Instant agora = Instant.now();
        repositorio.save(new OutboxEvento(
                id,
                evento.tipoAgregado(),
                evento.agregadoId(),
                evento.tipoEvento(),
                serializar(envelope(id, agora, evento)),
                agora));
    }

    private static Map<String, Object> envelope(UUID id, Instant agora, EventoDominio evento) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("specversion", "1.0");
        envelope.put("id", id.toString());
        envelope.put("source", SOURCE);
        envelope.put("type", evento.tipoEvento());
        envelope.put("time", agora.toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("dataschema", URL_SCHEMAS + evento.tipoEvento() + ".v1.json");
        envelope.put("subject", evento.agregadoId().toString());
        envelope.put("data", evento);
        return envelope;
    }

    private String serializar(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Não foi possível serializar o evento " + envelope.get("type"), e);
        }
    }
}
