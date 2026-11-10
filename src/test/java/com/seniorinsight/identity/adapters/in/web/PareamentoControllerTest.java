package com.seniorinsight.identity.adapters.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.seniorinsight.identity.TesteIntegracao;
import com.seniorinsight.identity.application.pareamento.ResgatarCodigoPareamento;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade.Tokens;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Corpo da resposta do POST /v1/pareamentos/resgates. O fluxo completo, com Keycloak real, está no PareamentoTest. */
@TesteIntegracao
class PareamentoControllerTest {

    private static final Tokens TOKENS = new Tokens("access", "refresh", 300, 1800L);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ResgatarCodigoPareamento resgatarCodigo;

    @Test
    void resgateDeCodigoComIdosoIdDevolveOId() throws Exception {
        UUID idosoId = UUID.randomUUID();
        UUID sessaoId = UUID.randomUUID();
        when(resgatarCodigo.executar(any())).thenReturn(resultado(sessaoId, idosoId));

        resgatar()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idosoId").value(idosoId.toString()))
                .andExpect(jsonPath("$.sessaoAparelhoId").value(sessaoId.toString()))
                .andExpect(jsonPath("$.tokens.accessToken").value("access"));
    }

    @Test
    void resgateDeCodigoSemIdosoIdDevolveNull() throws Exception {
        when(resgatarCodigo.executar(any())).thenReturn(resultado(UUID.randomUUID(), null));

        resgatar()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idosoId").value(nullValue()));
    }

    @Test
    void openApiDocumentaOIdosoId() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ResultadoPareamento.properties.idosoId").exists());
    }

    private ResultActions resgatar() throws Exception {
        return mvc.perform(post("/v1/pareamentos/resgates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"K7P2QX\",\"identificadorAparelho\":\"aparelho-1\",\"plataforma\":\"IOS\"}"));
    }

    private static ResgatarCodigoPareamento.Resultado resultado(UUID sessaoId, UUID idosoId) {
        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", Instant.now().truncatedTo(ChronoUnit.MICROS));
        return new ResgatarCodigoPareamento.Resultado(perfil, sessaoId, idosoId, TOKENS);
    }
}
