package com.seniorinsight.identity.application.porta.saida;

import java.time.Duration;
import java.time.Instant;

import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;

/** Códigos de pareamento pendentes. Cada operação é atômica: não há dois resgates do mesmo código. */
public interface RepositorioCodigosPareamento {

    /**
     * Guarda o código e invalida o anterior do mesmo familiar para o mesmo idoso (reemissão).
     *
     * @param retencao quanto tempo, depois de vencer, o código ainda responde "expirado" em vez de "não existe"
     * @return {@code false} se o código sorteado já está em uso (o chamador sorteia outro)
     */
    boolean salvar(CodigoPareamento codigo, Duration retencao);

    /**
     * Resgata o código: se estiver válido, apaga e deixa a marca de "já resgatado" na mesma operação.
     *
     * @param agora instante usado para decidir se o código venceu
     */
    Consumo consumir(String codigo, Instant agora, Duration retencao);

    /** Desfaz um {@link #consumir} cujo resgate falhou depois (Keycloak ou banco fora), se nada mudou nesse meio-tempo. */
    void devolver(CodigoPareamento codigo, Duration retencao);

    /** Apaga os códigos vigentes gerados pelo familiar, para nenhum deles ser resgatado depois da exclusão dele. */
    void excluirDoFamiliar(String familiarSub);

    sealed interface Consumo {
        record Resgatado(CodigoPareamento codigo) implements Consumo {
        }

        record JaResgatado() implements Consumo {
        }

        record Expirado() implements Consumo {
        }

        record Inexistente() implements Consumo {
        }
    }
}
