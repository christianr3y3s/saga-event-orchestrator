package com.lab.logistica.importacao.api.dto;

import com.lab.logistica.importacao.dominio.ResumoImportacao;
import java.util.List;

/** Forma de transporte HTTP de {@link ResumoImportacao}. */
public record ImportacaoResumo(
        String arquivo,
        int totalLinhas,
        int importadas,
        int rejeitadas,
        List<LinhaRejeitada> erros,
        List<AlertaConsumo> alertasConsumo
) {
    public static ImportacaoResumo deDominio(ResumoImportacao d) {
        return new ImportacaoResumo(
                d.arquivo(),
                d.totalLinhas(),
                d.importadas(),
                d.rejeitadas(),
                d.erros().stream().map(LinhaRejeitada::deDominio).toList(),
                d.alertasConsumo().stream().map(AlertaConsumo::deDominio).toList());
    }
}
