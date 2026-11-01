package com.seniorinsight.identity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Valida um JSON contra um schema de evento copiado do si-contracts para {@code src/test/resources/contracts/}.
 * Os {@code format} (uuid, date-time, email) são checados, não só anotados.
 */
public final class ValidadorContrato {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonSchema schema;

    private ValidadorContrato(JsonSchema schema) {
        this.schema = schema;
    }

    /** @param arquivo nome do arquivo em {@code contracts/}, ex.: {@code user.created.v1.json} */
    public static ValidadorContrato doEvento(String arquivo) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        JsonSchemaFactory fabrica = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        return new ValidadorContrato(fabrica.getSchema(ler(arquivo), config));
    }

    public Set<ValidationMessage> validar(String json) {
        try {
            return schema.validate(JSON.readTree(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode ler(String arquivo) {
        try (InputStream entrada = ValidadorContrato.class.getResourceAsStream("/contracts/" + arquivo)) {
            if (entrada == null) {
                throw new IllegalArgumentException("Contrato não encontrado: " + arquivo);
            }
            return JSON.readTree(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
