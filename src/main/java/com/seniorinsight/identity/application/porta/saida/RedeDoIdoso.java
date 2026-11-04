package com.seniorinsight.identity.application.porta.saida;

import java.util.UUID;

/** Rede de proteção do idoso, mantida pelo care-service (RF-007). */
public interface RedeDoIdoso {

    /**
     * @param tokenFamiliar access token do familiar, repassado ao care-service
     * @return {@code true} se o familiar é Administrador da rede do idoso
     * @throws com.seniorinsight.identity.application.pareamento.RedeIndisponivel se o care-service não responder
     */
    boolean familiarEhAdministrador(UUID idosoId, String familiarSub, String tokenFamiliar);
}
