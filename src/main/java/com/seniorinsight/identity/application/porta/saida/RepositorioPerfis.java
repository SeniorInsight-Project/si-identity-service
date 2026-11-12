package com.seniorinsight.identity.application.porta.saida;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;

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

    /** Grava o nome e o {@code atualizadoEm} do perfil. */
    void atualizarNome(Perfil perfil);

    /** Apaga o perfil. Exige transação; as sessões de aparelho dele têm de ter sido apagadas antes. */
    void excluir(UUID perfilId);

    /** Até {@code limite} perfis do tipo com id maior que {@code depoisDe}, em ordem de id (paginação por chave). */
    List<Perfil> listarPorTipoApos(TipoPerfil tipo, UUID depoisDe, int limite);
}
