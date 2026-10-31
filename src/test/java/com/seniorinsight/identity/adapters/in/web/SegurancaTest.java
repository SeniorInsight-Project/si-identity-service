package com.seniorinsight.identity.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.seniorinsight.identity.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@TesteIntegracao
class SegurancaTest {

    @Autowired
    MockMvc mvc;

    @Test
    void endpointProtegidoSemTokenRetorna401() throws Exception {
        mvc.perform(get("/v1/eu"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void endpointProtegidoComTokenRetorna200SemCriarSessao() throws Exception {
        var token = jwt()
                .jwt(j -> j.subject("4f1c2a9e-0000-4000-8000-000000000001")
                        .claim("realm_access", Map.of("roles", List.of("familiar", "offline_access"))))
                .authorities(new ConversorRolesKeycloak());

        mvc.perform(get("/v1/eu").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("4f1c2a9e-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.roles", contains("familiar", "offline_access")))
                .andExpect(resultado -> assertThat(resultado.getRequest().getSession(false)).isNull());
    }

    @Test
    void healthEhPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void swaggerEhPublico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void demaisEndpointsDoActuatorExigemToken() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}
