package com.lab.logistica.importacao.api.dto;

import com.lab.logistica.importacao.dominio.AlertaConsumoMedido;

/**
 * Forma de transporte HTTP de {@link AlertaConsumoMedido}: linha importada com sucesso, mas
 * cujo consumo medido está abaixo do limite de controle.
 */
public record AlertaConsumo(int linha, String caminhao, double consumoKmL) {
    public static AlertaConsumo deDominio(AlertaConsumoMedido d) {
        return new AlertaConsumo(d.linha(), d.caminhao(), d.consumoKmL());
    }
}
