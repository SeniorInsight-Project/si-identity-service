package com.seniorinsight.identity.adapters.in.web;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Devolve a identidade lida do token, sem consultar o banco. Serve para validar a integração gateway/Keycloak. */
@RestController
public class EuController {

    @GetMapping("/v1/eu")
    @Operation(summary = "Identidade do token", description = "Retorna o sub e as roles de realm do JWT recebido.")
    public IdentidadeToken eu(@Parameter(hidden = true) JwtAuthenticationToken autenticacao) {
        List<String> roles = autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ConversorRolesKeycloak.PREFIXO))
                .map(authority -> authority.substring(ConversorRolesKeycloak.PREFIXO.length()))
                .sorted()
                .toList();
        return new IdentidadeToken(autenticacao.getName(), roles);
    }

    public record IdentidadeToken(String sub, List<String> roles) {
    }
}
