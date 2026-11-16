package com.seniorinsight.identity.adapters.in.mensageria;

import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fila {@code identity.q} ligada à exchange {@code si.events} pela routing key do pedido de exclusão. Mensagem
 * rejeitada vai para a exchange de dead-letter {@code identity.dlx} e fica na fila {@code identity.dlq} para análise.
 */
@Configuration(proxyBeanMethods = false)
public class MensageriaConfig {

    public static final String EXCHANGE_EVENTOS = "si.events";
    public static final String FILA = "identity.q";
    public static final String EXCHANGE_DEAD_LETTER = "identity.dlx";
    public static final String FILA_DEAD_LETTER = "identity.dlq";

    @Bean
    Declarables filaIdentity(PropriedadesExclusao exclusao) {
        TopicExchange eventos = ExchangeBuilder.topicExchange(EXCHANGE_EVENTOS).durable(true).build();
        DirectExchange deadLetter = ExchangeBuilder.directExchange(EXCHANGE_DEAD_LETTER).durable(true).build();
        Queue fila = QueueBuilder.durable(FILA)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(FILA_DEAD_LETTER)
                .build();
        Queue filaDeadLetter = QueueBuilder.durable(FILA_DEAD_LETTER).build();

        List<Declarable> declaraveis = List.of(eventos, deadLetter, fila, filaDeadLetter,
                BindingBuilder.bind(filaDeadLetter).to(deadLetter).with(FILA_DEAD_LETTER),
                BindingBuilder.bind(fila).to(eventos).with(exclusao.routingKey()));
        return new Declarables(declaraveis);
    }
}
