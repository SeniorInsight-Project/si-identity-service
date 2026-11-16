package com.seniorinsight.identity.application.pareamento;

public class SessaoNaoEncontrada extends RuntimeException {

    public SessaoNaoEncontrada() {
        super("Sessão do aparelho não encontrada.");
    }
}
