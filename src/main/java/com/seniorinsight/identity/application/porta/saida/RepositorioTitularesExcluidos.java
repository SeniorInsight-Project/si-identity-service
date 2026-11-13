package com.seniorinsight.identity.application.porta.saida;

/**
 * Titulares cuja conta foi excluída, guardados só pelo hash do {@code sub}. Serve para um access token ainda válido não
 * recriar o perfil depois da exclusão. Exige transação.
 *
 * <p>As duas operações travam o mesmo {@code sub} até o fim da transação, então a criação do perfil e a exclusão do
 * mesmo titular não andam ao mesmo tempo.
 */
public interface RepositorioTitularesExcluidos {

    /** Trava o {@code sub} e registra a exclusão; repetir não muda nada. */
    void registrarExclusao(String keycloakSub);

    /** Trava o {@code sub} e diz se ele foi excluído. */
    boolean bloquearEVerificarExclusao(String keycloakSub);
}
