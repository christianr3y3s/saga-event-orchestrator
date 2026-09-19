package com.lab.logistica.rota.api;

import com.lab.logistica.rota.domain.ProvedorDistanciasIndisponivelException;
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

    /** Falha do provedor externo de distância: 502, sem vazar o corpo/stack da resposta de lá. */
    @ExceptionHandler(ProvedorDistanciasIndisponivelException.class)
    public ResponseEntity<ErroResposta> tratarProvedorIndisponivel(ProvedorDistanciasIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResposta("Serviço de distância rodoviária indisponível no momento; tente novamente"));
    }
}
