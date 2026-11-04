package com.seniorinsight.identity.application.pareamento;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prazos do código de pareamento ({@code identity.pareamento.*}).
 *
 * @param validade quanto tempo o código vale depois de gerado. Padrão de 24 h (RN-020); o contrato fala em "poucos
 *                 minutos", divergência ainda aberta com o time
 * @param retencao quanto tempo, depois de vencido ou resgatado, o código ainda responde 410/409 em vez de 404
 */
@ConfigurationProperties("identity.pareamento")
public record PropriedadesPareamento(Duration validade, Duration retencao) {

    public PropriedadesPareamento {
        Objects.requireNonNull(validade, "identity.pareamento.validade");
        Objects.requireNonNull(retencao, "identity.pareamento.retencao");
        if (validade.isNegative() || validade.isZero()) {
            throw new IllegalArgumentException("identity.pareamento.validade precisa ser positiva");
        }
        if (retencao.isNegative()) {
            throw new IllegalArgumentException("identity.pareamento.retencao não pode ser negativa");
        }
    }
}
