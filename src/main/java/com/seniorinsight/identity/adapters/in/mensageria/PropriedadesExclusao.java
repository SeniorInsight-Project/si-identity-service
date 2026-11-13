package com.seniorinsight.identity.adapters.in.mensageria;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consumo de {@code lgpd.erasure.requested} (HU-022).
 *
 * @param routingKey routing key da {@code si.events} ligada à fila {@code identity.q}
 * @param tentativas quantas vezes o pedido é tentado antes de ir para a dead-letter (a primeira conta)
 * @param intervalo  espera entre uma tentativa e a seguinte quando o Keycloak ou o banco falham
 */
@ConfigurationProperties("identity.exclusao")
public record PropriedadesExclusao(String routingKey, int tentativas, Duration intervalo) {

    public PropriedadesExclusao {
        Objects.requireNonNull(routingKey, "identity.exclusao.routing-key");
        Objects.requireNonNull(intervalo, "identity.exclusao.intervalo");
        if (tentativas < 1) {
            throw new IllegalArgumentException("identity.exclusao.tentativas deve ser pelo menos 1");
        }
        if (intervalo.isNegative()) {
            throw new IllegalArgumentException("identity.exclusao.intervalo não pode ser negativo");
        }
    }
}
