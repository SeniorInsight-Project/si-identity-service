package com.seniorinsight.identity.adapters.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.identity.ValidadorContrato;
import com.seniorinsight.identity.domain.evento.ExclusaoConcluida;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** O payload gravado na outbox para {@code lgpd.erasure.completed} valida contra o contrato do si-contracts. */
class ContratoExclusaoConcluidaTest {

    private static final ValidadorContrato CONTRATO = ValidadorContrato.doEvento("lgpd.erasure.completed.v1.json");
    private static final UUID PROTOCOLO = UUID.fromString("44b69553-caa4-48a5-9e4b-22da0c4f2090");

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    void exemploDoSiContractsEhValido() {
        assertThat(CONTRATO.validar(ValidadorContrato.ler("lgpd.erasure.completed.v1.valid.json").toString()))
                .isEmpty();
    }

    @Test
    void payloadGravadoValidaContraOContratoEOSubjectEhOProtocolo() throws Exception {
        OutboxEvento linha = linhaGravada();

        assertThat(CONTRATO.validar(linha.getPayload())).isEmpty();
        JsonNode envelope = new ObjectMapper().readTree(linha.getPayload());
        assertThat(envelope.get("subject").asText()).isEqualTo(PROTOCOLO.toString());
        assertThat(envelope.get("source").asText()).isEqualTo("/identity-service");
        assertThat(envelope.get("type").asText()).isEqualTo("lgpd.erasure.completed").isEqualTo(linha.getTipoEvento());
        assertThat(envelope.get("data").get("protocolo").asText()).isEqualTo(PROTOCOLO.toString());
        assertThat(envelope.get("data").get("servico").asText()).isEqualTo("identity-service");
    }

    @Test
    void confirmacaoNaoCarregaIdentificadorDoTitular() throws Exception {
        JsonNode data = new ObjectMapper().readTree(linhaGravada().getPayload()).get("data");

        assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("protocolo", "servico");
    }

    private OutboxEvento linhaGravada() {
        OutboxEventoRepository repositorio = mock(OutboxEventoRepository.class);
        OutboxEvento[] gravado = new OutboxEvento[1];
        contexto.run(ctx -> {
            new PublicadorEventosOutbox(repositorio, ctx.getBean(ObjectMapper.class))
                    .publicar(ExclusaoConcluida.de(PROTOCOLO));
            ArgumentCaptor<OutboxEvento> linha = ArgumentCaptor.forClass(OutboxEvento.class);
            verify(repositorio).save(linha.capture());
            gravado[0] = linha.getValue();
        });
        assertThat(gravado[0]).isNotNull();
        return gravado[0];
    }
}
