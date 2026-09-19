package com.lab.logistica.rota.domain;

import java.math.BigDecimal;

public record ResultadoFrete(
        double consumoKmL,
        double litros,
        BigDecimal combustivel,
        BigDecimal operacional,
        BigDecimal pedagios,
        BigDecimal custoTotal,
        BigDecimal margem,
        BigDecimal frete,
        BigDecimal fretePorKm,
        BigDecimal fretePorTonelada, // null quando o caminhão viaja vazio (carga zero)
        boolean abaixoDoPiso
) {
}
