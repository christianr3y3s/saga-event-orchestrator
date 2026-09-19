package com.lab.logistica.importacao.api.dto;

public record ConsumoMedioResponse(
        String caminhao,
        double cargaToneladas,
        double consumoKmL,
        int amostras,
        String fonte // "historico" (média de entregas reais) ou "tabela_nominal" (sem histórico ainda)
) {
}
