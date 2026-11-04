package com.seniorinsight.identity.application.pareamento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.seniorinsight.identity.application.porta.saida.RedeDoIdoso;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.domain.pareamento.CodigoPareamento;
import org.springframework.stereotype.Service;

/**
 * Gera o código de pareamento do aparelho do idoso (HU-007, RF-006).
 *
 * <p>Com {@code idosoId}, só o Familiar Administrador da rede do idoso pode gerar: o vínculo é confirmado no
 * care-service (RF-007). Sem {@code idosoId} o idoso ainda não existe no care-service e quem gera é o familiar que
 * vai cadastrá-lo. Gerar de novo para o mesmo idoso invalida o código anterior.
 */
@Service
public class GerarCodigoPareamento {

    static final int TENTATIVAS = 5;

    private final RepositorioCodigosPareamento codigos;
    private final RedeDoIdoso rede;
    private final PropriedadesPareamento propriedades;

    public GerarCodigoPareamento(RepositorioCodigosPareamento codigos, RedeDoIdoso rede,
                                 PropriedadesPareamento propriedades) {
        this.codigos = codigos;
        this.rede = rede;
        this.propriedades = propriedades;
    }

    public CodigoPareamento executar(Comando comando) {
        if (comando.idosoId() != null
                && !rede.familiarEhAdministrador(comando.idosoId(), comando.familiarSub(), comando.tokenFamiliar())) {
            throw new FamiliarSemVinculo();
        }
        Instant expiraEm = Instant.now().plus(propriedades.validade()).truncatedTo(ChronoUnit.MILLIS);
        for (int tentativa = 0; tentativa < TENTATIVAS; tentativa++) {
            CodigoPareamento codigo = new CodigoPareamento(CodigoPareamento.sortear(), comando.familiarSub(),
                    comando.nomeIdoso().strip(), comando.idosoId(), expiraEm);
            if (codigos.salvar(codigo, propriedades.retencao())) {
                return codigo;
            }
        }
        throw new IllegalStateException("Nenhum código livre em " + TENTATIVAS + " sorteios");
    }

    /**
     * @param tokenFamiliar access token de quem chamou, repassado ao care-service para conferir o vínculo
     * @param idosoId       idoso já cadastrado no care-service, ou {@code null}
     */
    public record Comando(String familiarSub, String tokenFamiliar, String nomeIdoso, UUID idosoId) {

        public Comando {
            Objects.requireNonNull(familiarSub, "familiarSub");
            Objects.requireNonNull(nomeIdoso, "nomeIdoso");
        }
    }
}
