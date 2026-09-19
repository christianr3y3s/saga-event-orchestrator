package com.lab.logistica.importacao.dominio;

/**
 * Uma linha da planilha que não pôde ser importada, com o motivo. Tipo do domínio -- não
 * confundir com {@code api.dto.LinhaRejeitada}, que é a forma de transporte HTTP e mapeia
 * este record via {@code deDominio(...)}.
 */
public record LinhaRejeitadaImportacao(int linha, String motivo) {
}
