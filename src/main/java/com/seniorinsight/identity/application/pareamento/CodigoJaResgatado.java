package com.seniorinsight.identity.application.pareamento;

public class CodigoJaResgatado extends RuntimeException {

    public CodigoJaResgatado() {
        super("O código de pareamento já foi usado.");
    }

    public CodigoJaResgatado(Throwable causa) {
        super("O código de pareamento já foi usado.", causa);
    }
}
