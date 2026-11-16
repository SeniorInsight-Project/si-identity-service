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
import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.pareamento.RenovacaoNaoAutorizada;
import com.seniorinsight.identity.application.pareamento.RenovarSessaoAparelho;
import com.seniorinsight.identity.application.pareamento.ResgatarCodigoPareamento;
import com.seniorinsight.identity.application.pareamento.SessaoNaoEncontrada;
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

    @MockitoBean
    RenovarSessaoAparelho renovarSessao;

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

    @Test
    void renovacaoComSucessoDevolveTokens() throws Exception {
        UUID sessaoId = UUID.randomUUID();
        when(renovarSessao.executar(new RenovarSessaoAparelho.Comando(sessaoId, "refresh-atual")))
                .thenReturn(TOKENS);

        renovar(sessaoId, "{\"refreshToken\":\"refresh-atual\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.refreshExpiresIn").value(1800))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void renovacaoComSessaoInexistenteResponde404() throws Exception {
        UUID sessaoId = UUID.randomUUID();
        when(renovarSessao.executar(any())).thenThrow(new SessaoNaoEncontrada());

        renovar(sessaoId, "{\"refreshToken\":\"refresh-atual\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void renovacaoNaoAutorizadaResponde401() throws Exception {
        UUID sessaoId = UUID.randomUUID();
        when(renovarSessao.executar(any())).thenThrow(new RenovacaoNaoAutorizada("Sessão revogada."));

        renovar(sessaoId, "{\"refreshToken\":\"refresh-atual\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void renovacaoComKeycloakIndisponivelResponde503() throws Exception {
        UUID sessaoId = UUID.randomUUID();
        when(renovarSessao.executar(any())).thenThrow(new IdentidadeIndisponivel("Keycloak fora", null));

        renovar(sessaoId, "{\"refreshToken\":\"refresh-atual\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void renovacaoComCorpoInvalidoResponde400() throws Exception {
        renovar(UUID.randomUUID(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        renovar(UUID.randomUUID(), "{\"refreshToken\":\"" + "a".repeat(4097) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void openApiDocumentaRotaDeRenovacao() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/pareamentos/sessoes/{sessaoAparelhoId}/renovacoes'].post.operationId")
                        .value("renovarSessaoAparelho"))
                .andExpect(jsonPath("$.components.schemas.RenovarSessaoAparelhoRequest.properties.refreshToken")
                        .exists());
    }

    private ResultActions renovar(UUID sessaoId, String corpo) throws Exception {
        return mvc.perform(post("/v1/pareamentos/sessoes/{sessaoAparelhoId}/renovacoes", sessaoId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions resgatar() throws Exception {
        return mvc.perform(post("/v1/pareamentos/resgates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"K7P2QX\",\"identificadorAparelho\":\"aparelho-1\",\"plataforma\":\"IOS\"}"));
    }

    private static ResgatarCodigoPareamento.Resultado resultado(UUID sessaoId, UUID idosoId) {
        Perfil perfil = Perfil.novoIdoso("sub-idoso", "Maria", null, Instant.now().truncatedTo(ChronoUnit.MICROS));
        return new ResgatarCodigoPareamento.Resultado(perfil, sessaoId, idosoId, TOKENS);
    }
}
