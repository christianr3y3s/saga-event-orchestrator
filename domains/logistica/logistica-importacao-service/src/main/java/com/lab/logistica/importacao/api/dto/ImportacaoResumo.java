package com.lab.logistica.importacao.api.dto;

import java.util.List;

public record ImportacaoResumo(
        String arquivo,
        int totalLinhas,
        int importadas,
        int rejeitadas,
        List<LinhaRejeitada> erros,
        List<AlertaConsumo> alertasConsumo
) {
}
