package com.seniorinsight.identity.application.exclusao;

import com.seniorinsight.identity.application.porta.saida.ProvedorIdentidade;
import com.seniorinsight.identity.application.porta.saida.PublicadorEventos;
import com.seniorinsight.identity.application.porta.saida.RepositorioCodigosPareamento;
import com.seniorinsight.identity.application.porta.saida.RepositorioPedidosExclusao;
import com.seniorinsight.identity.application.porta.saida.RepositorioPerfis;
import com.seniorinsight.identity.application.porta.saida.RepositorioSessoesAparelho;
import com.seniorinsight.identity.application.porta.saida.RepositorioTitularesExcluidos;
import com.seniorinsight.identity.domain.evento.ExclusaoConcluida;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.exclusao.SituacaoPedido;
import com.seniorinsight.identity.domain.perfil.Perfil;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Exclusão da conta de um titular a pedido do report-service (HU-022, RF-015), em três etapas, para nunca chamar o
 * Keycloak dentro de uma transação do banco:
 *
 * <ol>
 *   <li>transação curta: registra o pedido como pendente, com o {@code sub} do titular;</li>
 *   <li>fora de transação: encerra as sessões e apaga o usuário no Keycloak (usuário que já não existe conta como
 *       sucesso);</li>
 *   <li>transação curta: marca o pedido como concluído, guarda o hash do {@code sub} como excluído, apaga as sessões
 *       de aparelho, o perfil e (se familiar) os códigos de pareamento dele, e grava {@code lgpd.erasure.completed}
 *       na outbox.</li>
 * </ol>
 *
 * <p>Pode ser repetido com o mesmo protocolo: se falhar no meio, o pedido continua pendente e a nova tentativa refaz
 * as etapas; depois de concluído, repetir não chama o Keycloak nem publica de novo. Duas entregas ao mesmo tempo
 * também publicam uma confirmação só, porque quem conclui o pedido primeiro é quem publica.
 */
@Service
public class ExcluirConta {

    private static final Logger log = LoggerFactory.getLogger(ExcluirConta.class);

    private final RepositorioPedidosExclusao pedidos;
    private final ProvedorIdentidade provedor;
    private final RepositorioTitularesExcluidos excluidos;
    private final RepositorioPerfis perfis;
    private final RepositorioSessoesAparelho sessoes;
    private final RepositorioCodigosPareamento codigos;
    private final PublicadorEventos publicador;
    private final TransactionOperations transacao;

    public ExcluirConta(RepositorioPedidosExclusao pedidos, ProvedorIdentidade provedor,
                        RepositorioTitularesExcluidos excluidos, RepositorioPerfis perfis,
                        RepositorioSessoesAparelho sessoes, RepositorioCodigosPareamento codigos,
                        PublicadorEventos publicador, TransactionOperations transacao) {
        this.pedidos = pedidos;
        this.provedor = provedor;
        this.excluidos = excluidos;
        this.perfis = perfis;
        this.sessoes = sessoes;
        this.codigos = codigos;
        this.publicador = publicador;
        this.transacao = transacao;
    }

    /**
     * @throws com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel se o Keycloak não responder; o
     *                                                                                  pedido fica pendente
     */
    public void executar(PedidoExclusao pedido) {
        if (transacao.execute(status -> pedidos.registrarPendente(pedido)) == SituacaoPedido.CONCLUIDO) {
            log.info("Exclusão {} já concluída; entrega repetida ignorada", pedido.protocolo());
            return;
        }
        provedor.excluirUsuario(pedido.titularSub());
        transacao.executeWithoutResult(status -> apagarDadosEConfirmar(pedido));
    }

    private void apagarDadosEConfirmar(PedidoExclusao pedido) {
        if (!pedidos.concluir(pedido.protocolo())) {
            log.info("Exclusão {} concluída por outra entrega", pedido.protocolo());
            return;
        }
        excluidos.registrarExclusao(pedido.titularSub());
        perfis.buscarPorKeycloakSub(pedido.titularSub()).ifPresent(this::apagarPerfil);
        if (pedido.tipoTitular() == TipoPerfil.FAMILIAR) {
            codigos.excluirDoFamiliar(pedido.titularSub());
        }
        publicador.publicar(ExclusaoConcluida.de(pedido.protocolo()));
        log.info("Exclusão {} concluída", pedido.protocolo());
    }

    private void apagarPerfil(Perfil perfil) {
        sessoes.excluirDoPerfil(perfil.id());
        perfis.excluir(perfil.id());
    }
}
