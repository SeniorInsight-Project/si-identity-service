package com.seniorinsight.identity.adapters.out.care;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url                 endereço interno do care-service, ex.: {@code http://care-service:8080}
 * @param papeisAdministrador valores de {@code papelAcesso} que contam como Familiar Administrador
 */
@ConfigurationProperties("identity.care-service")
public record PropriedadesCareService(URI url, List<String> papeisAdministrador) {

    public PropriedadesCareService {
        Objects.requireNonNull(url, "identity.care-service.url");
        papeisAdministrador = papeisAdministrador == null ? List.of() : List.copyOf(papeisAdministrador);
    }
}
