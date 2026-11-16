package com.seniorinsight.identity.adapters.out.keycloak;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Usuário e tokens do idoso no Keycloak, pela service account do client do serviço (ADR 0001).
 *
 * <ul>
 *   <li>Admin API: busca o usuário pelo e-mail técnico {@code idoso-<chave>@<dominio>} e cria se não existir. O realm
 *       usa o e-mail como username, por isso o e-mail técnico. Tira a default role (que inclui {@code familiar}) e dá
 *       {@code idoso} e {@code offline_access}.</li>
 *   <li>Token exchange V1 ({@code --features=token-exchange:v1}): a service account troca o próprio token pelo do
 *       usuário com {@code requested_subject}.</li>
 * </ul>
 */
@Component
class ProvedorIdentidadeKeycloak implements ProvedorIdentidade {

    static final String GRANT_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String TIPO_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    static final String TIPO_REFRESH_TOKEN = "urn:ietf:params:oauth:token-type:refresh_token";
    static final String ROLE_IDOSO = "idoso";
    static final Set<String> ROLES_DO_IDOSO = Set.of(ROLE_IDOSO, "offline_access");

    private final RestClient rest;
    private final PropriedadesKeycloak propriedades;

    ProvedorIdentidadeKeycloak(RestClient.Builder builder, PropriedadesKeycloak propriedades) {
        this.rest = builder.baseUrl(propriedades.url().toString()).build();
        this.propriedades = propriedades;
    }

    @Override
    public String obterOuCriarUsuarioIdoso(UUID chave, String nome) {
        try {
            String token = tokenDoServico().accessToken();
            String email = "idoso-" + chave + "@" + propriedades.dominioEmailIdoso();
            String id = buscarPorEmail(token, email);
            if (id == null) {
                id = criar(token, email, nome);
            }
            ajustarRoles(token, id);
            return id;
        } catch (RestClientException e) {
            throw new IdentidadeIndisponivel("Falha ao criar o usuário do idoso no Keycloak", e);
        }
    }

    @Override
    public void atualizarNome(String keycloakSub, String nome) {
        try {
            String token = tokenDoServico().accessToken();
            // Lê o usuário inteiro e devolve com o nome trocado: o PUT da Admin API com o corpo parcial apagaria
            // os atributos que não vierem nele. O realm exige firstName e lastName (sem eles o login falha) e o claim
            // name é os dois juntos: a primeira palavra é o firstName e o resto o lastName.
            String[] partes = nome.strip().split("\s+", 2);
            Map<String, Object> usuario = new HashMap<>(rest.get()
                    .uri("/admin/realms/{realm}/users/{id}", propriedades.realm(), keycloakSub)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    }));
            usuario.put("firstName", partes[0]);
            usuario.put("lastName", partes.length > 1 ? partes[1] : "");
            rest.put()
                    .uri("/admin/realms/{realm}/users/{id}", propriedades.realm(), keycloakSub)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(usuario)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IdentidadeIndisponivel("Falha ao atualizar o nome do usuário " + keycloakSub + " no Keycloak", e);
        }
    }

    @Override
    public void excluirUsuario(String keycloakSub) {
        try {
            String token = tokenDoServico().accessToken();
            ignorandoInexistente(() -> encerrarSessoes(token, keycloakSub));
            ignorandoInexistente(() -> apagar(token, keycloakSub));
        } catch (RestClientException e) {
            throw new IdentidadeIndisponivel("Falha ao excluir o usuário " + keycloakSub + " no Keycloak", e);
        }
    }

    private void encerrarSessoes(String token, String keycloakSub) {
        rest.post()
                .uri("/admin/realms/{realm}/users/{id}/logout", propriedades.realm(), keycloakSub)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    private void apagar(String token, String keycloakSub) {
        rest.delete()
                .uri("/admin/realms/{realm}/users/{id}", propriedades.realm(), keycloakSub)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }

    private static void ignorandoInexistente(Runnable chamada) {
        try {
            chamada.run();
        } catch (HttpClientErrorException.NotFound usuarioInexistente) {
            // Já apagado (por uma tentativa anterior, por exemplo): o resultado é o que se queria.
        }
    }

    @Override
    public Tokens emitirTokens(String keycloakSub) {
        try {
            MultiValueMap<String, String> formulario = credenciais();
            formulario.add("grant_type", GRANT_TOKEN_EXCHANGE);
            formulario.add("subject_token", tokenDoServico().accessToken());
            formulario.add("subject_token_type", TIPO_ACCESS_TOKEN);
            formulario.add("requested_subject", keycloakSub);
            formulario.add("requested_token_type", TIPO_REFRESH_TOKEN);
            RespostaToken resposta = postarToken(formulario);
            if (resposta == null || resposta.accessToken() == null || resposta.refreshToken() == null) {
                throw new IdentidadeIndisponivel("O token exchange não devolveu access e refresh token", null);
            }
            return new Tokens(resposta.accessToken(), resposta.refreshToken(), resposta.expiresIn(),
                    resposta.refreshExpiresIn());
        } catch (RestClientException e) {
            throw new IdentidadeIndisponivel("Falha no token exchange do usuário " + keycloakSub, e);
        }
    }

    private RespostaToken tokenDoServico() {
        if (propriedades.clientSecret().isBlank()) {
            throw new IdentidadeIndisponivel("identity.keycloak.client-secret (KEYCLOAK_CLIENT_SECRET) não configurado",
                    null);
        }
        MultiValueMap<String, String> formulario = credenciais();
        formulario.add("grant_type", "client_credentials");
        return postarToken(formulario);
    }

    private RespostaToken postarToken(MultiValueMap<String, String> formulario) {
        return rest.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", propriedades.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario)
                .retrieve()
                .body(RespostaToken.class);
    }

    private MultiValueMap<String, String> credenciais() {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("client_id", propriedades.clientId());
        formulario.add("client_secret", propriedades.clientSecret());
        return formulario;
    }

    private String buscarPorEmail(String token, String email) {
        List<Map<String, Object>> usuarios = rest.get()
                .uri("/admin/realms/{realm}/users?email={email}&exact=true&briefRepresentation=true",
                        propriedades.realm(), email)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return usuarios == null || usuarios.isEmpty() ? null : (String) usuarios.getFirst().get("id");
    }

    private String criar(String token, String email, String nome) {
        Map<String, Object> usuario = Map.of(
                "username", email,
                "email", email,
                "emailVerified", true,
                "enabled", true,
                "firstName", nome);
        try {
            URI local = rest.post()
                    .uri("/admin/realms/{realm}/users", propriedades.realm())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(usuario)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
            if (local == null) {
                throw new IdentidadeIndisponivel("O Keycloak criou o usuário sem devolver o Location", null);
            }
            String caminho = local.getPath();
            return caminho.substring(caminho.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException e) {
            // Dois resgates do mesmo idoso ao mesmo tempo: quem perdeu a corrida usa o usuário do outro.
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                String id = buscarPorEmail(token, email);
                if (id != null) {
                    return id;
                }
            }
            throw e;
        }
    }

    /** Deixa o usuário só com as roles do idoso: sem a default role de quem se cadastra (que dá {@code familiar}). */
    private void ajustarRoles(String token, String id) {
        List<Map<String, Object>> atuais = roles(token, "/admin/realms/{realm}/users/{id}/role-mappings/realm", id);
        List<Map<String, Object>> sobrando = atuais.stream()
                .filter(role -> !ROLES_DO_IDOSO.contains((String) role.get("name")))
                .toList();
        if (!sobrando.isEmpty()) {
            rest.method(HttpMethod.DELETE)
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", propriedades.realm(), id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(sobrando)
                    .retrieve()
                    .toBodilessEntity();
        }
        List<Map<String, Object>> faltando = roles(token,
                "/admin/realms/{realm}/users/{id}/role-mappings/realm/available", id).stream()
                .filter(role -> ROLES_DO_IDOSO.contains((String) role.get("name")))
                .toList();
        if (!faltando.isEmpty()) {
            rest.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", propriedades.realm(), id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(faltando)
                    .retrieve()
                    .toBodilessEntity();
        }
        if (atuais.stream().noneMatch(role -> ROLE_IDOSO.equals(role.get("name")))
                && faltando.stream().noneMatch(role -> ROLE_IDOSO.equals(role.get("name")))) {
            throw new IdentidadeIndisponivel("A role " + ROLE_IDOSO + " não existe no realm", null);
        }
    }

    private List<Map<String, Object>> roles(String token, String caminho, String id) {
        List<Map<String, Object>> roles = rest.get()
                .uri(caminho, propriedades.realm(), id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return roles == null ? List.of() : roles;
    }

    record RespostaToken(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         @JsonProperty("expires_in") long expiresIn,
                         @JsonProperty("refresh_expires_in") Long refreshExpiresIn) {
    }
}
