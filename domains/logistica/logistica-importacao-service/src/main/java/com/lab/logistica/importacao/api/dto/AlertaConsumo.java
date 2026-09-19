package com.lab.logistica.importacao.api.dto;

/** Linha importada com sucesso, mas cujo consumo medido está abaixo do limite de controle. */
public record AlertaConsumo(int linha, String caminhao, double consumoKmL) {
}
