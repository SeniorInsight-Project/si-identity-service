package com.seniorinsight.identity.application.pareamento;

public class FamiliarSemVinculo extends RuntimeException {

    public FamiliarSemVinculo() {
        super("Só o Familiar Administrador do idoso pode gerar o código de pareamento.");
    }

    public FamiliarSemVinculo(Throwable causa) {
        super("Só o Familiar Administrador do idoso pode gerar o código de pareamento.", causa);
    }
}
