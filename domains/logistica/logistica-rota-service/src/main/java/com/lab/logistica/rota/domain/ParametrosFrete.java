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
        pedagios = exigirNaoNegativo(pedagios == null ? BigDecimal.ZERO : pedagios, "Pedágios");
        custoOperacionalPorKm = exigirNaoNegativo(
                custoOperacionalPorKm == null ? BigDecimal.ZERO : custoOperacionalPorKm, "O custo operacional");
        margemPercentual = exigirNaoNegativo(
                margemPercentual == null ? BigDecimal.ZERO : margemPercentual, "A margem");
        if (pisoMinimo != null) {
            exigirNaoNegativo(pisoMinimo, "O piso");
        }
    }

    /** Valida que {@code valor} não é negativo; devolve o próprio valor para uso em cadeia. */
    private static BigDecimal exigirNaoNegativo(BigDecimal valor, String nomeParaMensagem) {
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(nomeParaMensagem + " não pode ser negativo(a)");
        }
        return valor;
    }

    /** Construtor de conveniência sem piso mínimo. */
    public ParametrosFrete(TipoCaminhao caminhao, double cargaToneladas, double distanciaKm,
                            BigDecimal precoDieselPorLitro, BigDecimal pedagios,
                            BigDecimal custoOperacionalPorKm, BigDecimal margemPercentual) {
        this(caminhao, cargaToneladas, distanciaKm, precoDieselPorLitro, pedagios,
                custoOperacionalPorKm, margemPercentual, null);
    }
}
