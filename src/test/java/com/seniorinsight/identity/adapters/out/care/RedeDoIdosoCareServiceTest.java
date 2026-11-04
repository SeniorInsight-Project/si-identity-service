package com.seniorinsight.identity.adapters.out.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.identity.application.pareamento.RedeIndisponivel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Leitura da rede do idoso no formato {@code MembroRede} do contrato {@code care-v1.yaml}. */
class RedeDoIdosoCareServiceTest {

    private static final UUID IDOSO = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final String URL = "http://care-service:8080/api/v1/idosos/" + IDOSO + "/rede";

    private MockRestServiceServer care;
    private RedeDoIdosoCareService rede;

    @BeforeEach
    void careService() {
        RestClient.Builder builder = RestClient.builder();
        care = MockRestServiceServer.bindTo(builder).build();
        rede = new RedeDoIdosoCareService(builder,
                new PropriedadesCareService(URI.create("http://care-service:8080"), List.of("ADMINISTRADOR")));
    }

    @Test
    void administradorDaRedeRepassandoOTokenDoFamiliar() {
        care.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-ana"))
                .andRespond(withSuccess(rede("sub-ana", "administrador"), MediaType.APPLICATION_JSON));

        assertThat(rede.familiarEhAdministrador(IDOSO, "sub-ana", "token-ana")).isTrue();
        care.verify();
    }

    @Test
    void membroComOutroPapelNaoEhAdministrador() {
        care.expect(requestTo(URL)).andRespond(withSuccess(rede("sub-ana", "FAMILIAR"), MediaType.APPLICATION_JSON));

        assertThat(rede.familiarEhAdministrador(IDOSO, "sub-ana", "token-ana")).isFalse();
    }

    @Test
    void familiarForaDaRedeNaoEhAdministrador() {
        care.expect(requestTo(URL))
                .andRespond(withSuccess(rede("sub-outro", "ADMINISTRADOR"), MediaType.APPLICATION_JSON));

        assertThat(rede.familiarEhAdministrador(IDOSO, "sub-ana", "token-ana")).isFalse();
    }

    @Test
    void acessoNegadoOuIdosoInexistenteNoCareServiceContamComoSemVinculo() {
        care.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(rede.familiarEhAdministrador(IDOSO, "sub-ana", "token-ana")).isFalse();

        care.reset();
        care.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(rede.familiarEhAdministrador(IDOSO, "sub-ana", "token-ana")).isFalse();
    }

    @Test
    void erroDoCareServiceViraRedeIndisponivel() {
        care.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> rede.familiarEhAdministrador(IDOSO, "sub-ana", "token-ana"))
                .isInstanceOf(RedeIndisponivel.class);
    }

    private static String rede(String usuarioId, String papelAcesso) {
        return """
                [{"vinculoId":"%s","familiarId":"%s","usuarioId":"%s","nomeCompleto":"Ana Souza",
                  "email":"ana@example.com","telefone":null,"grauParentesco":"FILHA","papelAcesso":"%s",
                  "restricoesAcesso":{},"vinculadoEm":"2026-11-01T12:00:00Z"}]
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), usuarioId, papelAcesso);
    }
}
