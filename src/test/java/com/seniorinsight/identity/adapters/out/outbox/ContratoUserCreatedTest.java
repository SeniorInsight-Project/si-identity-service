package com.seniorinsight.identity.adapters.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seniorinsight.identity.ValidadorContrato;
import com.seniorinsight.identity.domain.evento.UserCreated;
import com.seniorinsight.identity.domain.perfil.Perfil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** O payload gravado na outbox para {@code user.created} valida contra {@code events/user.created.v1.json}. */
class ContratoUserCreatedTest {

    private static final ValidadorContrato CONTRATO = ValidadorContrato.doEvento("user.created.v1.json");

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    void exemploDoSiContractsEhValido() {
        assertThat(CONTRATO.validar(ValidadorContrato.ler("user.created.v1.valid.json").toString())).isEmpty();
    }

    @Test
    void payloadDoFamiliarValidaContraOContrato() {
        String payload = payloadGravado(Perfil.novoFamiliar("f1e2d3c4-b5a6-4789-8abc-def012345678",
                "Ana Souza", "ana@example.com", Instant.now().truncatedTo(ChronoUnit.MICROS)));

        assertThat(CONTRATO.validar(payload)).isEmpty();
    }

    @Test
    void payloadSemEmailValidaContraOContrato() {
        String payload = payloadGravado(Perfil.novoFamiliar("sub-sem-email", "ana", null, Instant.now()));

        assertThat(CONTRATO.validar(payload)).isEmpty();
    }

    @Test
    void envelopeLevaOsMetadadosDoEventoEAOutbox() throws Exception {
        Perfil perfil = Perfil.novoFamiliar("sub-1", "Ana", "ana@example.com", Instant.now());
        OutboxEvento linha = linhaGravada(perfil);
        JsonNode envelope = new ObjectMapper().readTree(linha.getPayload());

        assertThat(envelope.get("id").asText()).isEqualTo(linha.getId().toString());
        assertThat(envelope.get("subject").asText()).isEqualTo(perfil.id().toString());
        assertThat(envelope.get("type").asText()).isEqualTo("user.created").isEqualTo(linha.getTipoEvento());
        assertThat(envelope.get("dataschema").asText()).isEqualTo(
                "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/user.created.v1.json");
        assertThat(envelope.get("data").get("perfilId").asText()).isEqualTo(perfil.id().toString());
    }

    @Test
    void contratoRecusaPayloadForaDoFormato() throws Exception {
        String payload = payloadGravado(Perfil.novoFamiliar("sub-1", "Ana", "ana@example.com", Instant.now()));
        ObjectNode envelope = (ObjectNode) new ObjectMapper().readTree(payload);
        ((ObjectNode) envelope.get("data")).put("email", "nao-eh-email");
        ((ObjectNode) envelope.get("data")).put("extra", 1);

        assertThat(CONTRATO.validar(envelope.toString())).hasSizeGreaterThanOrEqualTo(2);
    }

    private String payloadGravado(Perfil perfil) {
        return linhaGravada(perfil).getPayload();
    }

    /** Usa o ObjectMapper configurado pelo Spring Boot, o mesmo que o serviço usa em produção. */
    private OutboxEvento linhaGravada(Perfil perfil) {
        OutboxEventoRepository repositorio = mock(OutboxEventoRepository.class);
        OutboxEvento[] gravado = new OutboxEvento[1];
        contexto.run(ctx -> {
            new PublicadorEventosOutbox(repositorio, ctx.getBean(ObjectMapper.class)).publicar(UserCreated.de(perfil));
            ArgumentCaptor<OutboxEvento> linha = ArgumentCaptor.forClass(OutboxEvento.class);
            verify(repositorio).save(linha.capture());
            gravado[0] = linha.getValue();
        });
        assertThat(gravado[0]).isNotNull();
        return gravado[0];
    }
}
