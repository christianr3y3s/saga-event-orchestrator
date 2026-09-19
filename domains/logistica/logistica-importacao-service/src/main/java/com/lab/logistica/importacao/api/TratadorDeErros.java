package com.lab.logistica.importacao.api;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> tratarEntradaInvalida(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErroResposta(ex.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErroResposta> tratarArquivoIlegivel(IOException ex) {
        return ResponseEntity.badRequest().body(new ErroResposta(
                "Não foi possível ler o arquivo como planilha .xlsx: " + ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> tratarArquivoGrandeDemais(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErroResposta("Arquivo maior que o limite permitido (ver app.importacao.tamanho-maximo)"));
    }
}
