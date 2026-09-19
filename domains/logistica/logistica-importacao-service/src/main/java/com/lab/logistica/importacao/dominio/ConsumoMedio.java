package com.lab.logistica.importacao.dominio;

/**
 * Resultado de {@link HistoricoConsumoService#consumoMedio(String, double)}. Tipo do
 * domínio -- a forma de transporte HTTP é {@code api.dto.ConsumoMedioResponse}, que mapeia
 * este record via {@code deDominio(...)}.
 */
public record ConsumoMedio(
        String caminhao,
        double cargaToneladas,
        double consumoKmL,
        int amostras,
        FonteConsumo fonte
) {
}
