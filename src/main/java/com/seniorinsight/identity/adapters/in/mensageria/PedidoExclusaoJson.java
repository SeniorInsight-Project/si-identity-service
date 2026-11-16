package com.seniorinsight.identity.adapters.in.mensageria;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.identity.domain.exclusao.PedidoExclusao;
import com.seniorinsight.identity.domain.perfil.TipoPerfil;
import org.springframework.stereotype.Component;

/**
 * Lê e valida o envelope CloudEvents de {@code lgpd.erasure.requested} conforme
 * {@code events/lgpd.erasure.requested.v1.json} do si-contracts: {@code source}, {@code type}, {@code dataschema},
 * {@code id} igual ao {@code message_id} da mensagem e {@code subject} igual ao protocolo.
 */
@Component
class PedidoExclusaoJson {

    static final String TIPO = "lgpd.erasure.requested";

    private static final String SPECVERSION = "1.0";
    private static final String SOURCE = "/report-service";
    private static final String DATACONTENTTYPE = "application/json";
    private static final String DATASCHEMA =
            "https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.requested.v1.json";
    private static final int TAMANHO_MAXIMO_DO_SUB = 255;
    private static final Pattern UUID_CANONICO =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ObjectMapper json;

    PedidoExclusaoJson(ObjectMapper json) {
        this.json = json;
    }

    PedidoExclusao ler(String mensagemId, byte[] corpo) {
        JsonNode envelope = lerEnvelope(corpo);
        conferirEnvelope(envelope, mensagemId);
        JsonNode data = envelope.path("data");
        UUID protocolo = uuid(data, "protocolo");
        uuid(data, "titularId");
        if (!protocolo.equals(uuid(envelope, "subject"))) {
            throw new PedidoExclusaoInvalido("subject diferente do protocolo");
        }
        return new PedidoExclusao(protocolo, tipoTitular(data), titularSub(data));
    }

    private JsonNode lerEnvelope(byte[] corpo) {
        JsonNode envelope;
        try {
            envelope = json.readTree(corpo);
        } catch (IOException e) {
            throw new PedidoExclusaoInvalido("o corpo não é JSON");
        }
        if (envelope == null || !envelope.isObject()) {
            throw new PedidoExclusaoInvalido("o corpo não é um objeto JSON");
        }
        return envelope;
    }

    private static void conferirEnvelope(JsonNode envelope, String mensagemId) {
        exigir(envelope, "specversion", SPECVERSION);
        exigir(envelope, "source", SOURCE);
        exigir(envelope, "type", TIPO);
        exigir(envelope, "dataschema", DATASCHEMA);
        exigir(envelope, "datacontenttype", DATACONTENTTYPE);
        if (!uuid(envelope, "id").toString().equalsIgnoreCase(mensagemId)) {
            throw new PedidoExclusaoInvalido("id diferente do message_id da mensagem");
        }
        try {
            Instant.parse(envelope.path("time").asText());
        } catch (DateTimeParseException e) {
            throw new PedidoExclusaoInvalido("time não é uma data e hora");
        }
    }

    private static void exigir(JsonNode envelope, String campo, String esperado) {
        if (!esperado.equals(envelope.path(campo).textValue())) {
            throw new PedidoExclusaoInvalido(campo + " diferente de " + esperado);
        }
    }

    private static TipoPerfil tipoTitular(JsonNode data) {
        try {
            return TipoPerfil.valueOf(data.path("tipoTitular").asText());
        } catch (IllegalArgumentException e) {
            throw new PedidoExclusaoInvalido("tipoTitular deve ser IDOSO ou FAMILIAR");
        }
    }

    private static String titularSub(JsonNode data) {
        String sub = data.path("titularSub").textValue();
        if (sub == null || sub.isBlank() || sub.length() > TAMANHO_MAXIMO_DO_SUB) {
            throw new PedidoExclusaoInvalido("titularSub ausente ou fora do tamanho");
        }
        return sub;
    }

    /** UUID na forma canônica: {@code UUID.fromString} aceita variações que o formato {@code uuid} do contrato não. */
    private static UUID uuid(JsonNode pai, String campo) {
        String texto = pai.path(campo).textValue();
        if (texto == null || !UUID_CANONICO.matcher(texto).matches()) {
            throw new PedidoExclusaoInvalido(campo + " não é um UUID");
        }
        return UUID.fromString(texto);
    }
}
