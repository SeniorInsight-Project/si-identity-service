package com.seniorinsight.identity.adapters.out.keycloak;

import java.net.URI;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acesso do serviço ao Keycloak como client confidencial (service account). Ver docs/adr/0001-token-do-idoso.md.
 *
 * @param url               endereço do Keycloak acessível pelo serviço (sem o {@code /realms/...}), ex.:
 *                          {@code http://keycloak:8080}
 * @param realm             realm, ex.: {@code seniorinsight}
 * @param clientId          client com service account, {@code manage-users} e {@code impersonation}
 * @param clientSecret      secret do client; vazio desliga o pareamento (as chamadas falham com 503)
 * @param dominioEmailIdoso domínio do e-mail técnico do usuário do idoso no Keycloak (o realm exige e-mail)
 */
@ConfigurationProperties("identity.keycloak")
public record PropriedadesKeycloak(URI url, String realm, String clientId, String clientSecret,
                                   String dominioEmailIdoso) {

    public PropriedadesKeycloak {
        Objects.requireNonNull(url, "identity.keycloak.url");
        Objects.requireNonNull(realm, "identity.keycloak.realm");
        Objects.requireNonNull(clientId, "identity.keycloak.client-id");
        Objects.requireNonNull(dominioEmailIdoso, "identity.keycloak.dominio-email-idoso");
        clientSecret = clientSecret == null ? "" : clientSecret;
    }
}
