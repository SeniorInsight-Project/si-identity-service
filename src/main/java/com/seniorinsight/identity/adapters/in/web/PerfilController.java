package com.seniorinsight.identity.adapters.in.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.seniorinsight.identity.application.perfil.AtualizarMeuPerfil;
import com.seniorinsight.identity.application.perfil.ObterOuCriarPerfil;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Perfil")
public class PerfilController {

    private final ObterOuCriarPerfil obterOuCriarPerfil;
    private final AtualizarMeuPerfil atualizarMeuPerfil;

    public PerfilController(ObterOuCriarPerfil obterOuCriarPerfil, AtualizarMeuPerfil atualizarMeuPerfil) {
        this.obterOuCriarPerfil = obterOuCriarPerfil;
        this.atualizarMeuPerfil = atualizarMeuPerfil;
    }

    @GetMapping("/v1/me")
    @Operation(operationId = "obterMeuPerfil", summary = "Perfil do usuário autenticado",
            description = "No primeiro acesso de um usuário com o papel familiar, cria o perfil FAMILIAR com o nome "
                    + "e o e-mail do token e publica o evento user.created. Os acessos seguintes só leem o perfil.")
    @ApiResponse(responseCode = "200", description = "Perfil")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Usuário sem perfil e sem o papel familiar",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    public PerfilResponse me(@Parameter(hidden = true) JwtAuthenticationToken autenticacao) {
        Jwt jwt = autenticacao.getToken();
        Set<String> papeis = autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ConversorRolesKeycloak.PREFIXO))
                .map(authority -> authority.substring(ConversorRolesKeycloak.PREFIXO.length()))
                .collect(Collectors.toSet());
        Perfil perfil = obterOuCriarPerfil.executar(new ObterOuCriarPerfil.Comando(
                jwt.getSubject(),
                papeis,
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email")));
        return PerfilResponse.de(perfil);
    }

    @PutMapping("/v1/me")
    @Operation(operationId = "atualizarMeuPerfil", summary = "Atualiza o perfil do usuário autenticado",
            description = "Atualiza o nome no perfil e no Keycloak. E-mail e credenciais são gerenciados no Keycloak "
                    + "e não mudam por aqui.")
    @ApiResponse(responseCode = "200", description = "Perfil atualizado")
    @ApiResponse(responseCode = "400", description = "Corpo inválido",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Usuário sem perfil",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    public PerfilResponse atualizar(@Valid @RequestBody AtualizarPerfilRequest requisicao,
                                    @Parameter(hidden = true) JwtAuthenticationToken autenticacao) {
        Perfil perfil = atualizarMeuPerfil.executar(
                new AtualizarMeuPerfil.Comando(autenticacao.getToken().getSubject(), requisicao.nome()));
        return PerfilResponse.de(perfil);
    }

    /** Schema {@code AtualizarPerfilRequest} do contrato {@code openapi/identity-v1.yaml}. */
    @Schema(name = "AtualizarPerfilRequest")
    public record AtualizarPerfilRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 150)
            @NotBlank @Size(max = 150) String nome) {
    }

    /** Schema {@code Perfil} do contrato {@code openapi/identity-v1.yaml}. */
    @Schema(name = "Perfil")
    public record PerfilResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TipoPerfil tipo,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 150,
                    example = "Maria Aparecida da Silva") String nome,
            @Schema(nullable = true, format = "email", maxLength = 320) String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant criadoEm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant atualizadoEm) {

        static PerfilResponse de(Perfil perfil) {
            return new PerfilResponse(perfil.id(), perfil.tipo(), perfil.nome(), perfil.email(), perfil.criadoEm(),
                    perfil.atualizadoEm());
        }
    }
}
