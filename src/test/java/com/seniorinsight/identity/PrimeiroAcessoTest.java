package com.seniorinsight.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Critérios de aceite da HU-001 de ponta a ponta: token real emitido por um Keycloak 26 (realm mínimo de teste em
 * {@code keycloak/realm-teste.json}), PostgreSQL e RabbitMQ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class PrimeiroAcessoTest {

    private static final String FILA = "teste.identity.user-created";
    private static final String SENHA = "senha-de-teste";

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.3")
            .withRealmImportFile("/keycloak/realm-teste.json");

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registro) {
        registro.add("KEYCLOAK_ISSUER_URI", PrimeiroAcessoTest::issuer);
    }

    private static final ValidadorContrato CONTRATO = ValidadorContrato.doEvento("user.created.v1.json");

    @Autowired
    MockMvc mvc;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void filaLigadaAoUserCreated() {
        Queue fila = new Queue(FILA, false, false, true);
        amqpAdmin.declareQueue(fila);
        Binding ligacao = BindingBuilder.bind(fila).to(new TopicExchange("si.events")).with("user.created");
        amqpAdmin.declareBinding(ligacao);
        amqpAdmin.purgeQueue(FILA, false);
    }

    @Test
    void primeiroAcessoDoFamiliarCriaOPerfilEPublicaUmUnicoUserCreated() throws Exception {
        String token = tokenDe("familiar.teste");

        JsonNode primeiro = getMe(token);
        JsonNode segundo = getMe(token);

        assertThat(segundo).isEqualTo(primeiro);
        assertThat(primeiro.get("tipo").asText()).isEqualTo("FAMILIAR");
        assertThat(primeiro.get("nome").asText()).isEqualTo("Ana Souza");
        assertThat(primeiro.get("email").asText()).isEqualTo("ana.souza@example.com");
        assertThat(primeiro.has("keycloakSub")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM perfil", Integer.class)).isEqualTo(1);

        Message mensagem = rabbitTemplate.receive(FILA, Duration.ofSeconds(15).toMillis());
        assertThat(mensagem).as("user.created publicado na si.events").isNotNull();
        String corpo = new String(mensagem.getBody(), StandardCharsets.UTF_8);
        assertThat(CONTRATO.validar(corpo)).isEmpty();

        JsonNode envelope = objectMapper.readTree(corpo);
        assertThat(mensagem.getMessageProperties().getMessageId()).isEqualTo(envelope.get("id").asText());
        assertThat(envelope.get("subject").asText()).isEqualTo(primeiro.get("id").asText());
        assertThat(envelope.at("/data/perfilId").asText()).isEqualTo(primeiro.get("id").asText());
        assertThat(envelope.at("/data/nome").asText()).isEqualTo("Ana Souza");

        assertThat(rabbitTemplate.receive(FILA, Duration.ofSeconds(3).toMillis()))
                .as("o segundo GET /v1/me não publica outro evento")
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'user.created'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void usuarioSemPerfilESemPapelFamiliarRecebe404() throws Exception {
        mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe("sem.papel.teste")))
                .andExpect(status().isNotFound());
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
    }

    private JsonNode getMe(String token) throws Exception {
        String corpo = mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo);
    }

    /** Password grant no client público de teste (só existe no realm de teste). */
    private String tokenDe(String usuario) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "password");
        formulario.add("client_id", "si-teste");
        formulario.add("scope", "openid");
        formulario.add("username", usuario);
        formulario.add("password", SENHA);
        JsonNode resposta = RestClient.create().post()
                .uri(issuer() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .body(JsonNode.class);
        return resposta.get("access_token").asText();
    }

    private static String issuer() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/seniorinsight";
    }
}
