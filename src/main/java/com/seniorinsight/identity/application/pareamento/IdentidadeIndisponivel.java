package com.seniorinsight.identity.application.pareamento;

/** O Keycloak não respondeu ou recusou a operação do serviço (criar o usuário do idoso, emitir os tokens). */
public class IdentidadeIndisponivel extends RuntimeException {

    public IdentidadeIndisponivel(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
