package com.seniorinsight.identity.application.perfil;

/** O nome não pode ser gravado no Keycloak: o realm exige nome e sobrenome, então precisa ter ao menos duas palavras. */
public class NomeInvalido extends RuntimeException {

    public NomeInvalido() {
        super("nome: informe nome e sobrenome");
    }
}
