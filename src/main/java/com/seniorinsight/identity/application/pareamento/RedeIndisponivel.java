package com.seniorinsight.identity.application.pareamento;

public class RedeIndisponivel extends RuntimeException {

    public RedeIndisponivel() {
        super("Não foi possível confirmar o vínculo com o idoso agora. Tente de novo em instantes.");
    }

    public RedeIndisponivel(Throwable causa) {
        super("Não foi possível confirmar o vínculo com o idoso agora. Tente de novo em instantes.", causa);
    }
}
