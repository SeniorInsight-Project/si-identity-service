package com.seniorinsight.identity.application.porta.saida;

import java.util.UUID;

/** Contas e tokens no Keycloak. Ver docs/adr/0001-token-do-idoso.md. */
public interface ProvedorIdentidade {

    /**
     * Busca o usuário do idoso pela chave ou cria, com a role {@code idoso} e sem as roles padrão de quem se cadastra.
     *
     * @return claim {@code sub} do usuário
     */
    String obterOuCriarUsuarioIdoso(UUID chave, String nome);

    /** Emite os tokens do usuário por token exchange (impersonação pela service account do serviço). */
    Tokens emitirTokens(String keycloakSub);

    record Tokens(String accessToken, String refreshToken, long expiresIn, Long refreshExpiresIn) {
    }
}
