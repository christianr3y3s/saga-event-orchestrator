package com.lab.logistica.importacao.dominio;

/**
 * Linha importada com sucesso, mas cujo consumo medido está abaixo do limite de controle
 * ({@link Consumo#LIMITE_MIN_KM_L}). Tipo do domínio -- a forma de transporte HTTP é
 * {@code api.dto.AlertaConsumo}, que mapeia este record via {@code deDominio(...)}.
 */
public record AlertaConsumoMedido(int linha, String caminhao, double consumoKmL) {
}
