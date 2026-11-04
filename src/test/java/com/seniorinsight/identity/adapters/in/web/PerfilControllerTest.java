package com.seniorinsight.identity.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Respostas de erro do GET /v1/me. O caminho feliz, com token real do Keycloak, está no PrimeiroAcessoTest. */
@TesteIntegracao
class PerfilControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void semPerfilESemPapelFamiliarRetorna404ComOCorpoDeErroPadrao() throws Exception {
        var token = jwt()
                .jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("name", "Maria")
                        .claim("realm_access", Map.of("roles", List.of("idoso", "offline_access"))))
                .authorities(new ConversorRolesKeycloak());

        mvc.perform(get("/v1/me").with(token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/v1/me"));
    }

    @Test
    void openApiDocumentaOGetMe() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/me'].get.operationId").value("obterMeuPerfil"))
                .andExpect(jsonPath("$.paths['/v1/me'].get.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.Perfil.required").isArray());
    }
}
