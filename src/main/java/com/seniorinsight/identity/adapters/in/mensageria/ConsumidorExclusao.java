package com.seniorinsight.identity.adapters.in.mensageria;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.Channel;
import com.seniorinsight.identity.application.exclusao.ExcluirConta;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome {@code identity.q} com ack manual (HU-022).
 *
 * <ul>
 *   <li>O ack só sai depois de a exclusão terminar (e a confirmação estar na outbox).</li>
 *   <li>Envelope inválido não tem como dar certo: vai direto para {@code identity.dlq}, com log de erro.</li>
 *   <li>Falha do Keycloak ou do banco: tenta de novo, com o intervalo e o limite de
 *       {@link PropriedadesExclusao}. O pedido pendente fica registrado no banco desde a primeira tentativa. Estourado
 *       o limite, a mensagem vai para {@code identity.dlq} com um log de erro com o protocolo, para ninguém descobrir
 *       a dead-letter por acaso; reprocessar é devolvê-la à fila.</li>
 * </ul>
 */
@Component
class ConsumidorExclusao {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorExclusao.class);

    private final ExcluirConta excluirConta;
    private final PedidoExclusaoJson leitor;
    private final PropriedadesExclusao propriedades;

    ConsumidorExclusao(ExcluirConta excluirConta, PedidoExclusaoJson leitor, PropriedadesExclusao propriedades) {
        this.excluirConta = excluirConta;
        this.leitor = leitor;
        this.propriedades = propriedades;
    }

    @RabbitListener(queues = MensageriaConfig.FILA, ackMode = "MANUAL")
    void receber(Message mensagem, Channel canal) throws IOException {
        MessageProperties cabecalho = mensagem.getMessageProperties();
        long tag = cabecalho.getDeliveryTag();
        PedidoExclusao pedido;
        try {
            pedido = leitor.ler(cabecalho.getMessageId(), mensagem.getBody());
        } catch (PedidoExclusaoInvalido e) {
            log.error("Mensagem {} enviada para a dead-letter: {}", cabecalho.getMessageId(), e.getMessage());
            canal.basicNack(tag, false, false);
            return;
        }
        try {
            if (excluirComTentativas(pedido)) {
                canal.basicAck(tag, false);
            } else {
                canal.basicNack(tag, false, false);
            }
        } catch (InterruptedException e) {
            // Desligando: devolve a mensagem à fila; o pedido continua pendente no banco.
            Thread.currentThread().interrupt();
            canal.basicNack(tag, false, true);
        }
    }

    /** @return {@code false} se as tentativas acabaram sem a exclusão terminar */
    private boolean excluirComTentativas(PedidoExclusao pedido) throws InterruptedException {
        for (int tentativa = 1; ; tentativa++) {
            try {
                excluirConta.executar(pedido);
                return true;
            } catch (RuntimeException e) {
                if (tentativa >= propriedades.tentativas()) {
                    log.error("Exclusão {} não terminou em {} tentativas; a mensagem vai para a dead-letter e o pedido "
                            + "continua pendente", pedido.protocolo(), tentativa, e);
                    return false;
                }
                log.warn("Exclusão {} falhou na tentativa {} de {} ({}); nova tentativa em {}", pedido.protocolo(),
                        tentativa, propriedades.tentativas(), e.getMessage(), propriedades.intervalo());
                TimeUnit.NANOSECONDS.sleep(propriedades.intervalo().toNanos());
            }
        }
    }
}
