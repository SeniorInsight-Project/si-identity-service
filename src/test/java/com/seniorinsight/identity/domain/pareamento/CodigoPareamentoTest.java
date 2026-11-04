package com.seniorinsight.identity.domain.pareamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CodigoPareamentoTest {

    @Test
    void codigoSorteadoSegueOFormatoDoContratoESemCaracteresAmbiguos() {
        Set<String> sorteados = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> sorteados.add(CodigoPareamento.sortear()));

        assertThat(sorteados).allMatch(codigo -> codigo.matches("^[A-Z0-9]{6,8}$"))
                .allMatch(codigo -> codigo.chars().noneMatch(c -> "0O1IL".indexOf(c) >= 0))
                .hasSizeGreaterThan(990);
    }

    @Test
    void qrLevaOCodigoNoEsquemaDoApp() {
        CodigoPareamento codigo = new CodigoPareamento("K7P2QX", "sub", "Maria", null, Instant.now());

        assertThat(codigo.qrCodeConteudo()).isEqualTo("seniorinsight://pareamento?codigo=K7P2QX");
    }

    @Test
    void recusaCodigoForaDoFormato() {
        assertThatThrownBy(() -> new CodigoPareamento("k7p2qx", "sub", "Maria", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CodigoPareamento.formatoValido("ABC12")).isFalse();
        assertThat(CodigoPareamento.formatoValido("ABC123456")).isFalse();
    }

    @Test
    void venceNoInstanteDeExpiracao() {
        Instant expiraEm = Instant.parse("2026-11-04T12:00:00Z");
        CodigoPareamento codigo = new CodigoPareamento("K7P2QX", "sub", "Maria", null, expiraEm);

        assertThat(codigo.expirado(expiraEm.minusMillis(1))).isFalse();
        assertThat(codigo.expirado(expiraEm)).isTrue();
    }
}
