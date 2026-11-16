package com.seniorinsight.identity.adapters.in.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seniorinsight.identity.ContainersConfig;
import com.seniorinsight.identity.ValidadorContrato;
import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Do RabbitMQ real ao PostgreSQL e à outbox: a mensagem entra pela {@code si.events} com a routing key do pedido, é
 * consumida de {@code identity.q} e a confirmação sai na outbox. Só o Keycloak é simulado (a exclusão real no
 * Keycloak está em {@code PareamentoTest}). O intervalo entre tentativas é curto para o teste não esperar.
 */
@SpringBootTest(properties = {
        "KEYCLOAK_ISSUER_URI=http://keycloak.invalid/realms/seniorinsight",
        "identity.exclusao.intervalo=PT0.05S"})
@Import(ContainersConfig.class)
class ConsumoExclusaoIntegracaoTest {

    private static final String EXCHANGE = "si.events";
    private static final String ROUTING_KEY = "lgpd.erasure.requested";
    private static final Duration ESPERA = Duration.ofSeconds(30);

    @MockitoBean
    ProvedorIdentidade keycloak;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RepositorioPerfis perfis;

    @Autowired
    TransactionTemplate transacao;

    @Test
    void pedidoRecebidoApagaOUsuarioOPerfilEConfirmaUmaVez() {
        String sub = UUID.randomUUID().toString();
        guardarFamiliar(sub);
        UUID protocolo = UUID.randomUUID();

        rabbit.send(EXCHANGE, ROUTING_KEY, pedido(UUID.randomUUID(), protocolo, "FAMILIAR", sub));

        await().atMost(ESPERA).until(() -> confirmacoes(protocolo) == 1);
        verify(keycloak).excluirUsuario(sub);
        assertThat(perfis.buscarPorKeycloakSub(sub)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT situacao FROM exclusao_pedido WHERE protocolo = ?", String.class,
                protocolo)).isEqualTo("CONCLUIDO");
    }

    @Test
    void keycloakForaDoArNaoConfirmaETentaDeNovoAteVoltar() {
        String sub = UUID.randomUUID().toString();
        UUID protocolo = UUID.randomUUID();
        doThrow(new IdentidadeIndisponivel("fora do ar", null)).doThrow(new IdentidadeIndisponivel("fora do ar", null))
                .doNothing().when(keycloak).excluirUsuario(sub);

        rabbit.send(EXCHANGE, ROUTING_KEY, pedido(UUID.randomUUID(), protocolo, "IDOSO", sub));

        await().atMost(ESPERA).until(() -> confirmacoes(protocolo) == 1);
        verify(keycloak, times(3)).excluirUsuario(sub);
    }

    @Test
    void mesmoProtocoloEntregueDuasVezesNaoChamaOKeycloakDeNovoNemPublicaDeNovo() {
        String sub = UUID.randomUUID().toString();
        UUID protocolo = UUID.randomUUID();

        rabbit.send(EXCHANGE, ROUTING_KEY, pedido(UUID.randomUUID(), protocolo, "IDOSO", sub));
        await().atMost(ESPERA).until(() -> confirmacoes(protocolo) == 1);
        rabbit.send(EXCHANGE, ROUTING_KEY, pedido(UUID.randomUUID(), protocolo, "IDOSO", sub));

        await().during(Duration.ofSeconds(2)).atMost(ESPERA).until(() -> confirmacoes(protocolo) == 1);
        verify(keycloak, times(1)).excluirUsuario(sub);
    }

    @Test
    void envelopeInvalidoVaiParaIdentityDlqSemExcluirNada() {
        UUID id = UUID.randomUUID();
        ObjectNode invalido = envelope(id, UUID.randomUUID(), "IDOSO", UUID.randomUUID().toString());
        invalido.put("source", "/care-service");

        rabbit.send(EXCHANGE, ROUTING_KEY, mensagem(id, invalido));

        Message naDeadLetter = rabbit.receive("identity.dlq", ESPERA.toMillis());
        assertThat(naDeadLetter).isNotNull();
        assertThat(naDeadLetter.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        verify(keycloak, never()).excluirUsuario(anyString());
    }

    private void guardarFamiliar(String sub) {
        Perfil familiar = Perfil.novoFamiliar(sub, "Ana", null, Instant.now().truncatedTo(ChronoUnit.MICROS));
        transacao.executeWithoutResult(status -> perfis.inserirSeAusente(familiar));
    }

    private int confirmacoes(UUID protocolo) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'lgpd.erasure.completed' AND agregado_id = ?
                """, Integer.class, protocolo);
    }

    private static Message pedido(UUID id, UUID protocolo, String tipo, String sub) {
        return mensagem(id, envelope(id, protocolo, tipo, sub));
    }

    private static ObjectNode envelope(UUID id, UUID protocolo, String tipo, String sub) {
        ObjectNode envelope = ValidadorContrato.ler("lgpd.erasure.requested.v1.valid.json").deepCopy();
        envelope.put("id", id.toString());
        envelope.put("subject", protocolo.toString());
        ObjectNode data = (ObjectNode) envelope.get("data");
        data.put("protocolo", protocolo.toString());
        data.put("tipoTitular", tipo);
        data.put("titularSub", sub);
        return envelope;
    }

    private static Message mensagem(UUID id, ObjectNode envelope) {
        return MessageBuilder.withBody(envelope.toString().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(id.toString())
                .setType(ROUTING_KEY)
                .build();
    }
}
