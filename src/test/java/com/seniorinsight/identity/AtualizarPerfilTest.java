package com.seniorinsight.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
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
 * PUT /v1/me de ponta a ponta: Keycloak 26 real (realm do pareamento, com a service account do serviço),
 * PostgreSQL, RabbitMQ e Redis. Depois do PUT, o GET /v1/me e o token novo trazem o nome atualizado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class AtualizarPerfilTest {

    private static final String SENHA = "senha-de-teste";
    private static final String FAMILIAR = "ana.souza@example.com";

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.3")
            .withRealmImportFile("/keycloak/realm-pareamento.json");

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registro) {
        registro.add("KEYCLOAK_ISSUER_URI", AtualizarPerfilTest::issuer);
        registro.add("identity.keycloak.url", KEYCLOAK::getAuthServerUrl);
        registro.add("identity.keycloak.client-secret", () -> "segredo-de-teste");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void depoisDoPutOGetMeEOTokenNovoTrazemONomeAtualizado() throws Exception {
        String token = tokenDe(FAMILIAR);
        JsonNode antes = getMe(token);
        assertThat(antes.get("nome").asText()).isEqualTo("Ana Souza");
        assertThat(claimName(token)).isEqualTo("Ana Souza");

        String resposta = mvc.perform(put("/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana Souza Lima\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode atualizado = objectMapper.readTree(resposta);

        assertThat(atualizado.get("nome").asText()).isEqualTo("Ana Souza Lima");
        assertThat(atualizado.get("id")).isEqualTo(antes.get("id"));
        assertThat(atualizado.get("email")).isEqualTo(antes.get("email"));
        assertThat(atualizado.get("criadoEm")).isEqualTo(antes.get("criadoEm"));
        assertThat(atualizado.get("atualizadoEm").asText()).isGreaterThan(antes.get("atualizadoEm").asText());

        assertThat(getMe(token)).isEqualTo(atualizado);
        assertThat(jdbc.queryForObject("SELECT nome FROM perfil WHERE id = ?::uuid", String.class,
                atualizado.get("id").asText())).isEqualTo("Ana Souza Lima");
        assertThat(claimName(tokenDe(FAMILIAR))).isEqualTo("Ana Souza Lima");
    }

    private JsonNode getMe(String token) throws Exception {
        String corpo = mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo);
    }

    private String claimName(String token) throws Exception {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return objectMapper.readTree(payload).get("name").asText();
    }

    /** Password grant no client público de teste (só existe no realm de teste). */
    private static String tokenDe(String usuario) {
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
