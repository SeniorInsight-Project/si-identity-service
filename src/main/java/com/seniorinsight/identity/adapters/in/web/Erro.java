package com.seniorinsight.identity.adapters.in.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

/** Corpo de erro padrão (schema {@code Erro} do contrato), no mesmo formato do erro padrão do Spring Boot. */
@Schema(name = "Erro")
public record Erro(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String error,
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path) {

    static Erro de(HttpStatus status, String mensagem, HttpServletRequest requisicao) {
        return new Erro(Instant.now(), status.value(), status.getReasonPhrase(), mensagem,
                requisicao.getRequestURI());
    }
}
