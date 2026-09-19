package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Cobre cada ramo de validação/defaulting do construtor compacto de {@link ParametrosFrete}. */
class ParametrosFreteValidacaoTest {

    private static final BigDecimal UM = BigDecimal.ONE;
    private static final BigDecimal NEGATIVO = new BigDecimal("-1");

    private static ParametrosFrete criar(double dist, BigDecimal diesel, BigDecimal ped, BigDecimal op,
                                         BigDecimal margem, BigDecimal piso) {
        return new ParametrosFrete(TipoCaminhao.CARRETA_30T, 10.0, dist, diesel, ped, op, margem, piso);
    }

    @Test
    void distanciaNaNOuNaoPositivaEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> criar(Double.NaN, UM, UM, UM, UM, null));
        assertThrows(IllegalArgumentException.class, () -> criar(0.0, UM, UM, UM, UM, null));
        assertThrows(IllegalArgumentException.class, () -> criar(-5.0, UM, UM, UM, UM, null));
    }

    @Test
    void dieselNuloOuNaoPositivoEhRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, null, UM, UM, UM, null));
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, BigDecimal.ZERO, UM, UM, UM, null));
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, NEGATIVO, UM, UM, UM, null));
    }

    @Test
    void camposOpcionaisNulosViramZero() {
        var p = criar(10.0, UM, null, null, null, null);
        assertEquals(BigDecimal.ZERO, p.pedagios());
        assertEquals(BigDecimal.ZERO, p.custoOperacionalPorKm());
        assertEquals(BigDecimal.ZERO, p.margemPercentual());
    }

    @Test
    void camposNegativosSaoRejeitadosUmAUm() {
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, UM, NEGATIVO, UM, UM, null));
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, UM, UM, NEGATIVO, UM, null));
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, UM, UM, UM, NEGATIVO, null));
        assertThrows(IllegalArgumentException.class, () -> criar(10.0, UM, UM, UM, UM, NEGATIVO));
    }

    @Test
    void pisoValidoEhPreservado() {
        assertEquals(new BigDecimal("100"), criar(10.0, UM, UM, UM, UM, new BigDecimal("100")).pisoMinimo());
    }
}
