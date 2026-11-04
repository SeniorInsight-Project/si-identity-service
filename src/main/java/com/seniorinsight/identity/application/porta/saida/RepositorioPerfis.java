package com.seniorinsight.identity.application.porta.saida;

import java.util.Optional;

import com.seniorinsight.identity.domain.perfil.Perfil;

public interface RepositorioPerfis {

    Optional<Perfil> buscarPorKeycloakSub(String keycloakSub);

    /**
     * Insere o perfil se ainda não existir outro com o mesmo {@code keycloakSub}.
     *
     * <p>Não lança erro na violação do unique: se uma transação concorrente inseriu o mesmo sub, espera ela terminar
     * e devolve {@code false}, sem abortar a transação de quem chamou.
     *
     * @return {@code true} se o perfil foi inserido
     */
    boolean inserirSeAusente(Perfil perfil);
}
