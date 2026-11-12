package com.seniorinsight.identity.adapters.out.keycloak;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Exclusão do usuário pela Admin API: encerra as sessões e apaga; usuário que já não existe conta como sucesso. */
class ProvedorIdentidadeKeycloakExclusaoTest {

    private static final String SUB = "7d1c4a52-0b8e-4f0a-9a52-3c1f5d2e9b10";
    private static final String TOKEN_URL = "http://keycloak:8080/realms/seniorinsight/protocol/openid-connect/token";
    private static final String USUARIO_URL = "http://keycloak:8080/admin/realms/seniorinsight/users/" + SUB;
    private static final String LOGOUT_URL = USUARIO_URL + "/logout";

    private MockRestServiceServer keycloak;
    private ProvedorIdentidadeKeycloak provedor;

    @BeforeEach
    void keycloak() {
        RestClient.Builder builder = RestClient.builder();
        keycloak = MockRestServiceServer.bindTo(builder).build();
        provedor = new ProvedorIdentidadeKeycloak(builder, new PropriedadesKeycloak(URI.create("http://keycloak:8080"),
                "seniorinsight", "si-identity-service", "segredo", "idoso.seniorinsight.invalid"));
    }

    @Test
    void encerraAsSessoesEApagaOUsuarioComOTokenDoServico() {
        tokenDoServico();
        keycloak.expect(requestTo(LOGOUT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-do-servico"))
                .andRespond(withNoContent());
        keycloak.expect(requestTo(USUARIO_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-do-servico"))
                .andRespond(withNoContent());

        provedor.excluirUsuario(SUB);

        keycloak.verify();
    }

    @Test
    void usuarioJaApagadoContaComoSucesso() {
        tokenDoServico();
        keycloak.expect(requestTo(LOGOUT_URL)).andRespond(withResourceNotFound());
        keycloak.expect(requestTo(USUARIO_URL)).andRespond(withResourceNotFound());

        assertThatCode(() -> provedor.excluirUsuario(SUB)).doesNotThrowAnyException();
        keycloak.verify();
    }

    @Test
    void usuarioApagadoEntreOLogoutEAExclusaoContaComoSucesso() {
        tokenDoServico();
        keycloak.expect(requestTo(LOGOUT_URL)).andRespond(withNoContent());
        keycloak.expect(requestTo(USUARIO_URL)).andRespond(withResourceNotFound());

        assertThatCode(() -> provedor.excluirUsuario(SUB)).doesNotThrowAnyException();
    }

    @Test
    void falhaDoKeycloakNaExclusaoViraIdentidadeIndisponivel() {
        tokenDoServico();
        keycloak.expect(requestTo(LOGOUT_URL)).andRespond(withNoContent());
        keycloak.expect(requestTo(USUARIO_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provedor.excluirUsuario(SUB)).isInstanceOf(IdentidadeIndisponivel.class);
    }

    @Test
    void falhaNoLogoutNaoApagaOUsuario() {
        tokenDoServico();
        keycloak.expect(requestTo(LOGOUT_URL)).andRespond(withServerError());
        keycloak.expect(never(), requestTo(USUARIO_URL)).andRespond(withNoContent());

        assertThatThrownBy(() -> provedor.excluirUsuario(SUB)).isInstanceOf(IdentidadeIndisponivel.class);
        keycloak.verify();
    }

    private void tokenDoServico() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(
                "{\"access_token\":\"token-do-servico\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
    }
}
