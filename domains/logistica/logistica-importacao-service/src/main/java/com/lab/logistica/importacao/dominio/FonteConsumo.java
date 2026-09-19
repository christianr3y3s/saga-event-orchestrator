package com.lab.logistica.importacao.dominio;

/**
 * De onde veio o consumo calibrado devolvido por {@link HistoricoConsumoService}. Antes um
 * {@code String} solto ("historico" / "tabela_nominal") -- primitive obsession que deixava
 * o valor livre para digitar errado em qualquer ponto de comparação; um enum fecha o
 * conjunto de valores possíveis.
 */
public enum FonteConsumo {
    /** Média do consumo medido em entregas reais já importadas para este caminhão/bucket. */
    HISTORICO,
    /** Ainda sem histórico suficiente -- caiu para o valor nominal da tabela de fábrica. */
    TABELA_NOMINAL
}
