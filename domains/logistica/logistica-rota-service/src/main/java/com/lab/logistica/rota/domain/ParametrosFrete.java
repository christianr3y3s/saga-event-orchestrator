package com.lab.logistica.rota.domain;

import java.math.BigDecimal;

/**
 * Regras do frete (v0.1, a validar com o negócio -- ver domains/logistica/README.md):
 *  - combustível = litros x preço do diesel, com litros = distância / consumo(carga)
 *  - operacional = distância x custo operacional por km
 *  - custo total = combustível + operacional + pedágios
 *  - margem = markup sobre o custo total (não margem sobre o preço de venda)
 *  - frete = custo total + margem
 */
public record ParametrosFrete(
        TipoCaminhao caminhao,
        double cargaToneladas,
        double distanciaKm,
        BigDecimal precoDieselPorLitro,
        BigDecimal pedagios,
        BigDecimal custoOperacionalPorKm,
        BigDecimal margemPercentual,
        BigDecimal pisoMinimo // opcional -- pode ser null
) {
    public ParametrosFrete {
        if (Double.isNaN(distanciaKm) || distanciaKm <= 0) {
            throw new IllegalArgumentException("A distância deve ser maior que zero");
        }
        if (precoDieselPorLitro == null || precoDieselPorLitro.signum() <= 0) {
            throw new IllegalArgumentException("O preço do diesel deve ser maior que zero");
        }
        pedagios = pedagios == null ? BigDecimal.ZERO : pedagios;
        custoOperacionalPorKm = custoOperacionalPorKm == null ? BigDecimal.ZERO : custoOperacionalPorKm;
        margemPercentual = margemPercentual == null ? BigDecimal.ZERO : margemPercentual;
        if (pedagios.signum() < 0) {
            throw new IllegalArgumentException("Pedágios não podem ser negativos");
        }
        if (custoOperacionalPorKm.signum() < 0) {
            throw new IllegalArgumentException("O custo operacional não pode ser negativo");
        }
        if (margemPercentual.signum() < 0) {
            throw new IllegalArgumentException("A margem não pode ser negativa");
        }
        if (pisoMinimo != null && pisoMinimo.signum() < 0) {
            throw new IllegalArgumentException("O piso não pode ser negativo");
        }
    }

    /** Construtor de conveniência sem piso mínimo. */
    public ParametrosFrete(TipoCaminhao caminhao, double cargaToneladas, double distanciaKm,
                            BigDecimal precoDieselPorLitro, BigDecimal pedagios,
                            BigDecimal custoOperacionalPorKm, BigDecimal margemPercentual) {
        this(caminhao, cargaToneladas, distanciaKm, precoDieselPorLitro, pedagios,
                custoOperacionalPorKm, margemPercentual, null);
    }
}
