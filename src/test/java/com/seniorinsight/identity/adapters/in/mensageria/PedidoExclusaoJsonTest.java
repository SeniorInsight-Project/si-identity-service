package com.seniorinsight.identity.adapters.in.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seniorinsight.identity.ValidadorContrato;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Valida o envelope de {@code lgpd.erasure.requested} antes de qualquer exclusão. */
class PedidoExclusaoJsonTest {

    private static final String MENSAGEM_ID = "176f6ee8-3920-4ea4-b558-b50ee934825a";

    private final PedidoExclusaoJson leitor = new PedidoExclusaoJson(new ObjectMapper());

    @Test
    void exemploValidoDoSiContractsViraOPedido() {
        PedidoExclusao pedido = leitor.ler(MENSAGEM_ID, corpo(envelope()));

        assertThat(pedido).isEqualTo(new PedidoExclusao(UUID.fromString("44b69553-caa4-48a5-9e4b-22da0c4f2090"),
                TipoPerfil.IDOSO, "sub-de-teste"));
    }

    @Test
    void familiarTambemEhAceito() {
        PedidoExclusao pedido = leitor.ler(MENSAGEM_ID, corpo(com(e -> dados(e).put("tipoTitular", "FAMILIAR"))));

        assertThat(pedido.tipoTitular()).isEqualTo(TipoPerfil.FAMILIAR);
    }

    @Test
    void exemploInvalidoDoSiContractsEhRecusado() {
        byte[] invalido = ValidadorContrato.ler("lgpd.erasure.requested.v1.invalid.json").toString()
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> leitor.ler(MENSAGEM_ID, invalido)).isInstanceOf(PedidoExclusaoInvalido.class);
    }

    @Test
    void corpoQueNaoEhJsonOuNaoEhObjetoEhRecusado() {
        assertThatThrownBy(() -> leitor.ler(MENSAGEM_ID, "isto não é json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PedidoExclusaoInvalido.class);
        assertThatThrownBy(() -> leitor.ler(MENSAGEM_ID, "[]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PedidoExclusaoInvalido.class);
    }

    @Test
    void mensagemSemMessageIdOuComIdDiferenteDoEnvelopeEhRecusada() {
        assertThatThrownBy(() -> leitor.ler(null, corpo(envelope()))).isInstanceOf(PedidoExclusaoInvalido.class);
        assertThatThrownBy(() -> leitor.ler(UUID.randomUUID().toString(), corpo(envelope())))
                .isInstanceOf(PedidoExclusaoInvalido.class);
        assertThatThrownBy(() -> leitor.ler("nao-eh-uuid", corpo(envelope())))
                .isInstanceOf(PedidoExclusaoInvalido.class);
    }

    @Test
    void sourceTypeDataschemaEDatacontenttypeSaoConferidos() {
        assertRecusado(e -> e.put("source", "/care-service"));
        assertRecusado(e -> e.put("type", "lgpd.erasure.completed"));
        assertRecusado(e -> e.put("dataschema", "https://exemplo.com/outro.json"));
        assertRecusado(e -> e.put("datacontenttype", "text/plain"));
        assertRecusado(e -> e.put("specversion", "0.3"));
    }

    @Test
    void idETimeInvalidosSaoRecusados() {
        assertRecusado(e -> e.put("id", "nao-eh-uuid"));
        assertRecusado(e -> e.put("time", "ontem"));
        assertRecusado(e -> e.remove("time"));
    }

    @Test
    void subjectDiferenteDoProtocoloEhRecusado() {
        assertRecusado(e -> e.put("subject", UUID.randomUUID().toString()));
        assertRecusado(e -> e.remove("subject"));
    }

    @Test
    void dadosInvalidosSaoRecusados() {
        assertRecusado(e -> dados(e).put("protocolo", "nao-eh-uuid"));
        assertRecusado(e -> dados(e).put("titularId", "nao-eh-uuid"));
        assertRecusado(e -> dados(e).put("tipoTitular", "SOLICITANTE"));
        assertRecusado(e -> dados(e).remove("tipoTitular"));
        assertRecusado(e -> dados(e).remove("titularSub"));
        assertRecusado(e -> dados(e).put("titularSub", ""));
        assertRecusado(e -> dados(e).put("titularSub", "   "));
        assertRecusado(e -> dados(e).put("titularSub", "x".repeat(256)));
        assertRecusado(e -> dados(e).put("titularSub", 42));
        assertRecusado(e -> e.remove("data"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    void titularSubNoLimiteDoContratoEhAceito(int tamanho) {
        PedidoExclusao pedido = leitor.ler(MENSAGEM_ID, corpo(com(e -> dados(e).put("titularSub", "x".repeat(tamanho)))));

        assertThat(pedido.titularSub()).hasSize(tamanho);
    }

    private void assertRecusado(Consumer<ObjectNode> alteracao) {
        byte[] corpo = corpo(com(alteracao));
        assertThatThrownBy(() -> leitor.ler(MENSAGEM_ID, corpo)).isInstanceOf(PedidoExclusaoInvalido.class);
    }

    private static ObjectNode com(Consumer<ObjectNode> alteracao) {
        ObjectNode envelope = envelope();
        alteracao.accept(envelope);
        return envelope;
    }

    private static ObjectNode dados(ObjectNode envelope) {
        return (ObjectNode) envelope.get("data");
    }

    private static ObjectNode envelope() {
        return ValidadorContrato.ler("lgpd.erasure.requested.v1.valid.json").deepCopy();
    }

    private static byte[] corpo(ObjectNode envelope) {
        return envelope.toString().getBytes(StandardCharsets.UTF_8);
    }
}
