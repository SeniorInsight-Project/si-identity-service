package com.seniorinsight.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.identity.application.porta.saida.RedeDoIdoso;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HU-007 de ponta a ponta: Keycloak 26 real com o token exchange V1 ligado (ADR 0001) e um realm que reproduz o do
 * si-infra ({@code keycloak/realm-pareamento.json}), PostgreSQL, RabbitMQ e Redis. O care-service é simulado na porta
 * {@link RedeDoIdoso}; o adapter REST tem teste próprio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class PareamentoTest {

    private static final String SENHA = "senha-de-teste";
    private static final String FAMILIAR = "ana.souza@example.com";

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.3")
            .withFeaturesEnabled("token-exchange:v1")
            .withRealmImportFile("/keycloak/realm-pareamento.json");

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registro) {
        registro.add("KEYCLOAK_ISSUER_URI", PareamentoTest::issuer);
        registro.add("identity.keycloak.url", KEYCLOAK::getAuthServerUrl);
        registro.add("identity.keycloak.client-secret", () -> "segredo-de-teste");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RepositorioCodigosPareamento codigos;

    @MockitoBean
    RedeDoIdoso rede;

    /** Cenário 1: o Familiar Administrador gera o código e o QR do idoso. */
    @Test
    void familiarAdministradorGeraCodigoEQrDeUsoUnico() throws Exception {
        UUID idosoId = UUID.randomUUID();
        String token = tokenDe(FAMILIAR);
        when(rede.familiarEhAdministrador(eq(idosoId), anyString(), eq(token))).thenReturn(true);

        JsonNode codigo = gerar(token, "Maria Aparecida da Silva", idosoId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::json);

        assertThat(codigo.get("codigo").asText()).matches("^[A-Z0-9]{6,8}$");
        assertThat(codigo.get("qrCodeConteudo").asText())
                .isEqualTo("seniorinsight://pareamento?codigo=" + codigo.get("codigo").asText());
        assertThat(Instant.parse(codigo.get("expiraEm").asText()))
                .isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(1, ChronoUnit.MINUTES));
        verify(rede).familiarEhAdministrador(eq(idosoId), eq(subDe(token)), eq(token));
    }

    /** Cenário 2: o idoso digita o código no aparelho e entra sem e-mail nem senha. */
    @Test
    void idosoResgataOCodigoEEntraComOsTokensDoKeycloak() throws Exception {
        String codigo = codigoGerado("Maria Aparecida da Silva", null);

        JsonNode resultado = resgatar(codigo, "aparelho-cenario-2")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::json);

        JsonNode perfil = resultado.get("perfil");
        assertThat(perfil.get("tipo").asText()).isEqualTo("IDOSO");
        assertThat(perfil.get("nome").asText()).isEqualTo("Maria Aparecida da Silva");
        assertThat(perfil.get("email").isNull()).isTrue();
        JsonNode tokens = resultado.get("tokens");
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asLong()).isPositive();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        String accessToken = tokens.get("accessToken").asText();
        JsonNode claims = json(new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1])));
        assertThat(claims.at("/realm_access/roles")).extracting(JsonNode::asText)
                .contains("idoso")
                .doesNotContain("familiar");

        // O token do idoso vale no próprio serviço e leva ao perfil criado no resgate.
        mvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(perfil.get("id").asText()))
                .andExpect(jsonPath("$.tipo").value("IDOSO"));

        UUID perfilId = UUID.fromString(perfil.get("id").asText());
        assertThat(jdbc.queryForMap("""
                        SELECT perfil_id, identificador_aparelho, plataforma, pareado_por, revogado_em
                          FROM sessao_aparelho WHERE id = ?""",
                UUID.fromString(resultado.get("sessaoAparelhoId").asText())))
                .containsEntry("perfil_id", perfilId)
                .containsEntry("identificador_aparelho", "aparelho-cenario-2")
                .containsEntry("plataforma", "ANDROID")
                .containsEntry("pareado_por", subDe(tokenDe(FAMILIAR)))
                .containsEntry("revogado_em", null);
        String evento = jdbc.queryForObject(
                "SELECT payload::text FROM outbox_evento WHERE tipo_evento = 'user.created' AND agregado_id = ?",
                String.class, perfilId);
        assertThat(ValidadorContrato.doEvento("user.created.v1.json").validar(evento)).isEmpty();
        assertThat(json(evento).at("/data/tipo").asText()).isEqualTo("IDOSO");
        assertThat(json(evento).at("/data/email").isNull()).isTrue();
    }

    @Test
    void codigoUsadoDuasVezesResponde409() throws Exception {
        String codigo = codigoGerado("João Pereira", null);

        resgatar(codigo, "aparelho-409").andExpect(status().isOk());
        resgatar(codigo, "outro-aparelho").andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void codigoVencidoResponde410() throws Exception {
        String codigo = "VENC1D0";
        codigos.salvar(new CodigoPareamento(codigo, "sub-familiar", "Idoso Vencido", null,
                Instant.now().minus(1, ChronoUnit.MINUTES)), Duration.ofHours(1));

        resgatar(codigo, "aparelho-410").andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void familiarSemVinculoComOIdosoRecebe403() throws Exception {
        UUID idosoId = UUID.randomUUID();
        when(rede.familiarEhAdministrador(eq(idosoId), anyString(), anyString())).thenReturn(false);

        gerar(tokenDe(FAMILIAR), "Maria", idosoId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void usuarioSemRoleFamiliarRecebe403SemConsultarOCareService() throws Exception {
        gerar(tokenDe("medico@example.com"), "Maria", UUID.randomUUID()).andExpect(status().isForbidden());

        verify(rede, never()).familiarEhAdministrador(any(), any(), any());
    }

    @Test
    void codigoInexistenteResponde404() throws Exception {
        resgatar("ZZZZZZ", "aparelho-404").andExpect(status().isNotFound());
    }

    @Test
    void reemitirInvalidaOCodigoAnterior() throws Exception {
        UUID idosoId = UUID.randomUUID();
        when(rede.familiarEhAdministrador(eq(idosoId), anyString(), anyString())).thenReturn(true);
        String primeiro = codigoGerado("Antônio", idosoId);
        String segundo = codigoGerado("Antônio", idosoId);

        resgatar(primeiro, "aparelho-reemissao").andExpect(status().isNotFound());
        resgatar(segundo, "aparelho-reemissao").andExpect(status().isOk());
    }

    @Test
    void pareamentoDeNovoDoMesmoIdosoReaproveitaPerfilERevogaASessaoAnteriorDoAparelho() throws Exception {
        UUID idosoId = UUID.randomUUID();
        when(rede.familiarEhAdministrador(eq(idosoId), anyString(), anyString())).thenReturn(true);

        JsonNode primeiro = resgatar(codigoGerado("Benedita", idosoId), "aparelho-repareado")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(this::json);
        JsonNode segundo = resgatar(codigoGerado("Benedita", idosoId), "aparelho-repareado")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(this::json);

        String perfilId = primeiro.at("/perfil/id").asText();
        assertThat(segundo.at("/perfil/id").asText()).isEqualTo(perfilId);
        assertThat(jdbc.queryForObject("SELECT idoso_id FROM perfil WHERE id = ?", UUID.class,
                UUID.fromString(perfilId))).isEqualTo(idosoId);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'user.created' AND agregado_id = ?",
                Integer.class, UUID.fromString(perfilId))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT revogado_em IS NOT NULL FROM sessao_aparelho WHERE id = ?", Boolean.class,
                UUID.fromString(primeiro.get("sessaoAparelhoId").asText()))).isTrue();
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM sessao_aparelho
                         WHERE perfil_id = ? AND identificador_aparelho = 'aparelho-repareado' AND revogado_em IS NULL""",
                Integer.class, UUID.fromString(perfilId))).isEqualTo(1);
    }

    @Test
    void requisicaoForaDoContratoResponde400() throws Exception {
        mvc.perform(post("/v1/pareamentos/resgates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"abc\",\"identificadorAparelho\":\"x\",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/pareamentos/resgates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"ABC123\",\"identificadorAparelho\":\"x\",\"plataforma\":\"WINDOWS\"}"))
                .andExpect(status().isBadRequest());
        gerar(tokenDe(FAMILIAR), " ", null).andExpect(status().isBadRequest());
    }

    @Test
    void gerarSemTokenResponde401() throws Exception {
        mvc.perform(post("/v1/pareamentos/codigos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeIdoso\":\"Maria\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String codigoGerado(String nomeIdoso, UUID idosoId) throws Exception {
        return gerar(tokenDe(FAMILIAR), nomeIdoso, idosoId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::json)
                .get("codigo").asText();
    }

    private ResultActions gerar(String token, String nomeIdoso, UUID idosoId) throws Exception {
        String corpo = objectMapper.writeValueAsString(idosoId == null
                ? Map.of("nomeIdoso", nomeIdoso)
                : Map.of("nomeIdoso", nomeIdoso, "idosoId", idosoId));
        return mvc.perform(post("/v1/pareamentos/codigos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions resgatar(String codigo, String aparelho) throws Exception {
        return mvc.perform(post("/v1/pareamentos/resgates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "codigo", codigo, "identificadorAparelho", aparelho, "plataforma", "ANDROID"))));
    }

    private JsonNode json(String texto) {
        try {
            return objectMapper.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String subDe(String token) {
        return json(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]))).get("sub").asText();
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
