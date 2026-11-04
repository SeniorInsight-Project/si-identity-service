package com.seniorinsight.identity.application.pareamento;

public class CodigoNaoEncontrado extends RuntimeException {

    public CodigoNaoEncontrado() {
        super("Não existe código de pareamento com esse valor (ou ele foi substituído por um mais novo).");
    }

    public CodigoNaoEncontrado(Throwable causa) {
        super("Não existe código de pareamento com esse valor (ou ele foi substituído por um mais novo).", causa);
    }
}
