package com.seniorinsight.identity.adapters.in.web;

import java.util.stream.Collectors;

import com.seniorinsight.identity.application.pareamento.CodigoExpirado;
import com.seniorinsight.identity.application.pareamento.CodigoJaResgatado;
import com.seniorinsight.identity.application.pareamento.CodigoNaoEncontrado;
import com.seniorinsight.identity.application.pareamento.FamiliarSemVinculo;
import com.seniorinsight.identity.application.pareamento.IdentidadeIndisponivel;
import com.seniorinsight.identity.application.pareamento.RedeIndisponivel;
import com.seniorinsight.identity.application.perfil.PerfilNaoEncontrado;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class TratadorErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler({PerfilNaoEncontrado.class, CodigoNaoEncontrado.class})
    ResponseEntity<Erro> naoEncontrado(RuntimeException e, HttpServletRequest requisicao) {
        return erro(HttpStatus.NOT_FOUND, e.getMessage(), requisicao);
    }

    @ExceptionHandler(FamiliarSemVinculo.class)
    ResponseEntity<Erro> semVinculo(FamiliarSemVinculo e, HttpServletRequest requisicao) {
        return erro(HttpStatus.FORBIDDEN, e.getMessage(), requisicao);
    }

    @ExceptionHandler(CodigoJaResgatado.class)
    ResponseEntity<Erro> jaResgatado(CodigoJaResgatado e, HttpServletRequest requisicao) {
        return erro(HttpStatus.CONFLICT, e.getMessage(), requisicao);
    }

    @ExceptionHandler(CodigoExpirado.class)
    ResponseEntity<Erro> expirado(CodigoExpirado e, HttpServletRequest requisicao) {
        return erro(HttpStatus.GONE, e.getMessage(), requisicao);
    }

    @ExceptionHandler({RedeIndisponivel.class, IdentidadeIndisponivel.class})
    ResponseEntity<Erro> indisponivel(RuntimeException e, HttpServletRequest requisicao) {
        log.warn("Dependência indisponível em {}: {}", requisicao.getRequestURI(), e.getMessage(), e);
        return erro(HttpStatus.SERVICE_UNAVAILABLE,
                "Serviço temporariamente indisponível. Tente de novo em instantes.", requisicao);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Erro> invalido(MethodArgumentNotValidException e, HttpServletRequest requisicao) {
        String mensagem = e.getBindingResult().getFieldErrors().stream()
                .map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return erro(HttpStatus.BAD_REQUEST, mensagem, requisicao);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Erro> ilegivel(HttpMessageNotReadableException e, HttpServletRequest requisicao) {
        return erro(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou malformado", requisicao);
    }

    private static ResponseEntity<Erro> erro(HttpStatus status, String mensagem, HttpServletRequest requisicao) {
        return ResponseEntity.status(status).body(Erro.de(status, mensagem, requisicao));
    }
}
