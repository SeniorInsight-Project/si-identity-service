package com.seniorinsight.identity.adapters.in.web;

import com.seniorinsight.identity.application.perfil.PerfilNaoEncontrado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class TratadorErros {

    @ExceptionHandler(PerfilNaoEncontrado.class)
    ResponseEntity<Erro> perfilNaoEncontrado(PerfilNaoEncontrado e, HttpServletRequest requisicao) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Erro.de(HttpStatus.NOT_FOUND, e.getMessage(), requisicao));
    }
}
