package com.seniorinsight.identity.application.porta.saida;

import java.util.UUID;

import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;

public interface RepositorioSessoesAparelho {

    /** Registra a sessão e revoga a sessão ativa anterior do mesmo perfil no mesmo aparelho. Exige transação. */
    void registrar(SessaoAparelho sessao);

    /** Apaga todas as sessões do perfil, ativas e revogadas. Exige transação. */
    void excluirDoPerfil(UUID perfilId);
}
