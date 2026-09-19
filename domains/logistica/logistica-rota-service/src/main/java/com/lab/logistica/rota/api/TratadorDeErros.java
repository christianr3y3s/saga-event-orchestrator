package com.lab.logistica.rota.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz erro de validação de domínio (carga acima da capacidade, distância <= 0, etc.,
 * todos IllegalArgumentException) e corpo de requisição malformado em 400, com a
 * mensagem explicando o que corrigir -- em vez de vazar um 500 genérico.
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> tratarEntradaInvalida(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErroResposta(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> tratarCorpoInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("Corpo da requisição inválido ou incompleto"));
    }
}
