package com.seniorinsight.identity.application.perfil;

/** O usuário do token não tem perfil e não pode criá-lo no primeiro acesso (só o familiar pode). */
public class PerfilNaoEncontrado extends RuntimeException {

    public PerfilNaoEncontrado(String keycloakSub) {
        super("Perfil não encontrado para o usuário " + keycloakSub);
    }
}
