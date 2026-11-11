package com.seniorinsight.identity.adapters.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/** Respostas de erro do GET e do PUT /v1/me. O caminho feliz, com token real do Keycloak, está no PrimeiroAcessoTest. */
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

    @Test
    void putSemTokenRetorna401() throws Exception {
        mvc.perform(put("/v1/me").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Maria Silva\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nome\":\"\"}", "{\"nome\":\"   \"}", "{\"nome\":null}", "", "{nome"})
    void putComCorpoInvalidoRetorna400ComOCorpoDeErroPadrao(String corpo) throws Exception {
        mvc.perform(put("/v1/me").with(tokenDeFamiliar()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/v1/me"));
    }

    @Test
    void putComNomeDeUmaPalavraSoRetorna400() throws Exception {
        mvc.perform(put("/v1/me").with(tokenDeFamiliar()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("nome: informe nome e sobrenome"));
    }

    @Test
    void putComNomeMaiorQue150CaracteresRetorna400() throws Exception {
        mvc.perform(put("/v1/me").with(tokenDeFamiliar()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + "a".repeat(151) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("nome: size must be between 0 and 150"));
    }

    @Test
    void putSemPerfilRetorna404ComOCorpoDeErroPadrao() throws Exception {
        mvc.perform(put("/v1/me").with(tokenDeFamiliar()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Silva\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/v1/me"));
    }

    @Test
    void openApiDocumentaOPutMe() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/me'].put.operationId").value("atualizarMeuPerfil"))
                .andExpect(jsonPath("$.paths['/v1/me'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/v1/me'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/v1/me'].put.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.AtualizarPerfilRequest.required[0]").value("nome"))
                .andExpect(jsonPath("$.components.schemas.AtualizarPerfilRequest.properties.nome.maxLength")
                        .value(150));
    }

    private static JwtRequestPostProcessor tokenDeFamiliar() {
        return jwt()
                .jwt(j -> j.subject(UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("familiar"))))
                .authorities(new ConversorRolesKeycloak());
    }
}
