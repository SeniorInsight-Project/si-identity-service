package com.seniorinsight.identity.domain.pareamento;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aparelho do idoso pareado por código (linha de {@code sessao_aparelho}).
 *
 * @param pareadoPor claim {@code sub} do familiar que gerou o código resgatado
 */
public record SessaoAparelho(UUID id, UUID perfilId, String identificadorAparelho, Plataforma plataforma,
                             String pareadoPor, Instant criadoEm) {

    public SessaoAparelho {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(perfilId, "perfilId");
        Objects.requireNonNull(identificadorAparelho, "identificadorAparelho");
        Objects.requireNonNull(plataforma, "plataforma");
        Objects.requireNonNull(pareadoPor, "pareadoPor");
        Objects.requireNonNull(criadoEm, "criadoEm");
    }

    public static SessaoAparelho nova(UUID perfilId, String identificadorAparelho, Plataforma plataforma,
                                      String pareadoPor, Instant agora) {
        return new SessaoAparelho(UUID.randomUUID(), perfilId, identificadorAparelho, plataforma, pareadoPor, agora);
    }
}
