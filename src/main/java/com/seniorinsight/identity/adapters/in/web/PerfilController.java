package com.seniorinsight.identity.adapters.in.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.seniorinsight.identity.application.perfil.ObterOuCriarPerfil;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Perfil")
public class PerfilController {

    private final ObterOuCriarPerfil obterOuCriarPerfil;

    public PerfilController(ObterOuCriarPerfil obterOuCriarPerfil) {
        this.obterOuCriarPerfil = obterOuCriarPerfil;
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
