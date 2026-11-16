package com.seniorinsight.identity.application.perfil;

/**
 * A conta do usuário do token foi excluída (HU-022), mas o access token dele ainda vale: o perfil não é recriado.
 * É um {@link PerfilNaoEncontrado}, então responde 404 como o contrato de {@code /v1/me} já prevê.
 */
public class TitularExcluido extends PerfilNaoEncontrado {

    public TitularExcluido(String keycloakSub) {
        super(keycloakSub);
    }
}
