package com.seniorinsight.identity.application.porta.saida;

import com.seniorinsight.identity.domain.pareamento.SessaoAparelho;

public interface RepositorioSessoesAparelho {

    /** Registra a sessão e revoga a sessão ativa anterior do mesmo perfil no mesmo aparelho. Exige transação. */
    void registrar(SessaoAparelho sessao);
}
