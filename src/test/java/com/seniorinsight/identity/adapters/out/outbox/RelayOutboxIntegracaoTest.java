package com.seniorinsight.identity.adapters.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.domain.evento.EventoDominio;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Caminho completo: caso de uso grava na outbox, o job agendado publica no RabbitMQ e marca a linha. */
@TesteIntegracao
class RelayOutboxIntegracaoTest {

    private static final String ROUTING_KEY = "teste.relay";

    @Autowired
    PublicadorEventos publicador;

    @Autowired
    OutboxEventoRepository repositorio;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publicaOEnvelopeNaExchangeSiEventsComRoutingKeyIgualAoTipoDoEventoEMarcaPublicado() throws Exception {
        Queue fila = amqpAdmin.declareQueue();
        amqpAdmin.declareBinding(BindingBuilder.bind(fila)
                .to(new TopicExchange(OutboxConfig.EXCHANGE_EVENTOS))
                .with(ROUTING_KEY));
        UUID perfilId = UUID.randomUUID();

        transacao.executeWithoutResult(status -> publicador.publicar(new EventoTeste(perfilId, "Maria")));

        Message mensagem = rabbitTemplate.receive(fila.getName(), Duration.ofSeconds(15).toMillis());
        assertThat(mensagem).as("mensagem publicada pelo relay").isNotNull();
        MessageProperties propriedades = mensagem.getMessageProperties();
        assertThat(propriedades.getReceivedExchange()).isEqualTo("si.events");
        assertThat(propriedades.getReceivedRoutingKey()).isEqualTo(ROUTING_KEY);
        assertThat(propriedades.getType()).isEqualTo(ROUTING_KEY);
        assertThat(propriedades.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(propriedades.getAppId()).isEqualTo("identity-service");
        assertThat(propriedades.<String>getHeader("tipo_agregado")).isEqualTo("perfil");
        assertThat(propriedades.<String>getHeader("agregado_id")).isEqualTo(perfilId.toString());

        JsonNode envelope = objectMapper.readTree(mensagem.getBody());
        assertThat(envelope.get("specversion").asText()).isEqualTo("1.0");
        assertThat(envelope.get("id").asText()).isEqualTo(propriedades.getMessageId());
        assertThat(envelope.get("source").asText()).isEqualTo("/identity-service");
        assertThat(envelope.get("type").asText()).isEqualTo(ROUTING_KEY);
        assertThat(envelope.get("subject").asText()).isEqualTo(perfilId.toString());
        assertThat(envelope.get("datacontenttype").asText()).isEqualTo("application/json");
        assertThat(envelope.get("time").asText()).isNotBlank();
        assertThat(envelope.get("data"))
                .isEqualTo(objectMapper.createObjectNode().put("perfilId", perfilId.toString()).put("nome", "Maria"));

        UUID eventoId = UUID.fromString(propriedades.getMessageId());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvento evento = repositorio.findById(eventoId).orElseThrow();
            assertThat(evento.getAgregadoId()).isEqualTo(perfilId);
            assertThat(evento.getPublicadoEm()).isNotNull();
            assertThat(evento.getTentativas()).isZero();
        });
    }

    @Test
    void eventoNaoEhGravadoQuandoATransacaoDoCasoDeUsoEhDesfeita() {
        UUID perfilId = UUID.randomUUID();

        transacao.executeWithoutResult(status -> {
            publicador.publicar(new EventoTeste(perfilId, "João"));
            status.setRollbackOnly();
        });

        assertThat(repositorio.findAll()).noneMatch(evento -> evento.getAgregadoId().equals(perfilId));
    }

    @Test
    void publicarExigeTransacaoAtiva() {
        assertThatThrownBy(() -> publicador.publicar(new EventoTeste(UUID.randomUUID(), "Ana")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Routing key própria, para não receber eventos reais publicados por outros testes do mesmo contexto. */
    record EventoTeste(UUID perfilId, String nome) implements EventoDominio {

        @Override
        public String tipoAgregado() {
            return "perfil";
        }

        @Override
        public UUID agregadoId() {
            return perfilId;
        }

        @Override
        public String tipoEvento() {
            return ROUTING_KEY;
        }
    }
}
