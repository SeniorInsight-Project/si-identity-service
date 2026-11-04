package com.seniorinsight.identity.application.pareamento;

public class CodigoExpirado extends RuntimeException {

    public CodigoExpirado() {
        super("O código de pareamento expirou. Peça um novo ao familiar.");
    }

    public CodigoExpirado(Throwable causa) {
        super("O código de pareamento expirou. Peça um novo ao familiar.", causa);
    }
}
