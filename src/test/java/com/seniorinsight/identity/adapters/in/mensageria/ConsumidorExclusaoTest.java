package com.seniorinsight.identity.adapters.in.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.seniorinsight.identity.ValidadorContrato;
import com.seniorinsight.identity.application.exclusao.ExcluirConta;
import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** Ack manual, nova tentativa com intervalo e limite, e dead-letter para o que não tem como dar certo. */
class ConsumidorExclusaoTest {

    private static final long TAG = 42L;
    private static final String MENSAGEM_ID = "176f6ee8-3920-4ea4-b558-b50ee934825a";
    private static final PedidoExclusao PEDIDO = new PedidoExclusao(
            UUID.fromString("44b69553-caa4-48a5-9e4b-22da0c4f2090"), TipoPerfil.IDOSO, "sub-de-teste");

    private final ExcluirConta excluirConta = mock(ExcluirConta.class);
    private final Channel canal = mock(Channel.class);
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final Logger logDoConsumidor = (Logger) LoggerFactory.getLogger(ConsumidorExclusao.class);
    private final ConsumidorExclusao consumidor = new ConsumidorExclusao(excluirConta,
            new PedidoExclusaoJson(new ObjectMapper()),
            new PropriedadesExclusao("lgpd.erasure.requested", 3, Duration.ZERO));

    @BeforeEach
    void capturarLog() {
        log.start();
        logDoConsumidor.addAppender(log);
    }

    @AfterEach
    void soltarLog() {
        logDoConsumidor.detachAppender(log);
        Thread.interrupted();
    }

    @Test
    void pedidoValidoEhExcluidoEConfirmadoComAck() throws IOException {
        consumidor.receber(mensagem(valido()), canal);

        verify(excluirConta).executar(PEDIDO);
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void envelopeInvalidoVaiParaADeadLetterSemTentarExcluir() throws IOException {
        consumidor.receber(mensagem("{\"nao\":\"eh o envelope\"}"), canal);

        verifyNoInteractions(excluirConta);
        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        assertThat(log.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
    }

    @Test
    void keycloakForaDoArTentaDeNovoEConfirmaQuandoVolta() throws IOException {
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).doThrow(new IdentidadeIndisponivel("fora do ar", null))
                .doNothing().when(excluirConta).executar(PEDIDO);

        consumidor.receber(mensagem(valido()), canal);

        verify(excluirConta, times(3)).executar(PEDIDO);
        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertThat(log.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR);
    }

    @Test
    void falhaDoBancoTambemEhTentadaDeNovo() throws IOException {
        doThrow(new IllegalStateException("banco fora")).doNothing().when(excluirConta).executar(PEDIDO);

        consumidor.receber(mensagem(valido()), canal);

        verify(excluirConta, times(2)).executar(PEDIDO);
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void estourandoOLimiteVaiParaADeadLetterComLogDeErro() throws IOException {
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).when(excluirConta).executar(PEDIDO);

        consumidor.receber(mensagem(valido()), canal);

        verify(excluirConta, times(3)).executar(PEDIDO);
        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        assertThat(log.list).filteredOn(evento -> evento.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(evento -> assertThat(evento.getFormattedMessage())
                        .contains(PEDIDO.protocolo().toString(), "3")
                        .doesNotContain(PEDIDO.titularSub()));
    }

    @Test
    void consumidorInterrompidoDevolveAMensagemParaAFilaSemPerderOPedido() throws IOException {
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).when(excluirConta).executar(PEDIDO);
        ConsumidorExclusao comEspera = new ConsumidorExclusao(excluirConta, new PedidoExclusaoJson(new ObjectMapper()),
                new PropriedadesExclusao("lgpd.erasure.requested", 3, Duration.ofSeconds(30)));
        Thread.currentThread().interrupt();

        comEspera.receber(mensagem(valido()), canal);

        verify(canal).basicNack(TAG, false, true);
        verify(excluirConta).executar(PEDIDO);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static String valido() {
        return ValidadorContrato.ler("lgpd.erasure.requested.v1.valid.json").toString();
    }

    private static Message mensagem(String corpo) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setMessageId(MENSAGEM_ID);
        propriedades.setDeliveryTag(TAG);
        propriedades.setReceivedRoutingKey("lgpd.erasure.requested");
        return new Message(corpo.getBytes(StandardCharsets.UTF_8), propriedades);
    }
}
