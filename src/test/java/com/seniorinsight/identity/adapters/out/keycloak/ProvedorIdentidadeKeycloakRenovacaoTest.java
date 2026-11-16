package com.seniorinsight.identity.adapters.out.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.pareamento.RenovacaoNaoAutorizada;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.TokensRenovados;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProvedorIdentidadeKeycloakRenovacaoTest {

    private static final String TOKEN_URL = "http://keycloak:8080/realms/seniorinsight/protocol/openid-connect/token";

    private MockRestServiceServer keycloak;
    private ProvedorIdentidadeKeycloak provedor;

    @BeforeEach
    void configurar() {
        RestClient.Builder builder = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(builder).build();
        provedor = new ProvedorIdentidadeKeycloak(builder, new PropriedadesKeycloak(URI.create("http://keycloak:8080"),
                "seniorinsight", "si-identity-service", "segredo", "idoso.seniorinsight.invalid"));
    }

    @Test
    void renovaTokensComCredenciaisDoServicoEExtraiSub() {
        String access = jwtComSub("sub-idoso-123");
        keycloak.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "refresh_token",
                        "client_id", "si-identity-service",
                        "client_secret", "segredo",
                        "refresh_token", "refresh-antigo")))
                .andRespond(withSuccess("""
                        {"access_token":"%s","refresh_token":"refresh-novo","expires_in":300,"refresh_expires_in":2592000}
                        """.formatted(access), MediaType.APPLICATION_JSON));

        TokensRenovados renovados = provedor.renovarTokens("refresh-antigo");

        assertThat(renovados.keycloakSub()).isEqualTo("sub-idoso-123");
        assertThat(renovados.tokens().accessToken()).isEqualTo(access);
        assertThat(renovados.tokens().refreshToken()).isEqualTo("refresh-novo");
        assertThat(renovados.tokens().expiresIn()).isEqualTo(300L);
        assertThat(renovados.tokens().refreshExpiresIn()).isEqualTo(2592000L);
        keycloak.verify();
    }

    @Test
    void invalidGrantLancaRenovacaoNaoAutorizadaSemExporToken() {
        keycloak.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Token is not active\"}"));

        assertThatThrownBy(() -> provedor.renovarTokens("refresh-secreto"))
                .isInstanceOf(RenovacaoNaoAutorizada.class)
                .hasMessageNotContaining("refresh-secreto");
    }

    @Test
    void erro5xxDoKeycloakLancaIdentidadeIndisponivel() {
        keycloak.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provedor.renovarTokens("refresh-secreto"))
                .isInstanceOf(IdentidadeIndisponivel.class)
                .hasMessageNotContaining("refresh-secreto");
    }

    private static String jwtComSub(String sub) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".assinatura";
    }
}
