package com.seniorinsight.identity.application.pareamento;

public class RenovacaoNaoAutorizada extends RuntimeException {

    public RenovacaoNaoAutorizada(String mensagem) {
        super(mensagem);
    }

    public RenovacaoNaoAutorizada(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
