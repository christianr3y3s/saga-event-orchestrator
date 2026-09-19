package com.lab.logistica.importacao.api.dto;

import com.lab.logistica.importacao.dominio.LinhaRejeitadaImportacao;

/** Forma de transporte HTTP de {@link LinhaRejeitadaImportacao}. */
public record LinhaRejeitada(int linha, String motivo) {
    public static LinhaRejeitada deDominio(LinhaRejeitadaImportacao d) {
        return new LinhaRejeitada(d.linha(), d.motivo());
    }
}
