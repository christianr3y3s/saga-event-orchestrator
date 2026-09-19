package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Casos A, B e C são os mesmos "casos de aceitação" da página web de frete e do app Android. */
class CalculadoraFreteTest {

    private final CalculadoraFrete calculadora = new CalculadoraFrete();

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private ParametrosFrete casoA(BigDecimal piso) {
        return new ParametrosFrete(TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0,
                bd("6.00"), bd("120.00"), bd("2.50"), bd("15"), piso);
    }

    @Test
    void casoA_carretaCarregada() {
        var r = calculadora.calcular(casoA(null));
        assertEquals(2.65, r.consumoKmL(), 1e-9);
        assertEquals(bd("1132.08"), r.combustivel());
        assertEquals(bd("1250.00"), r.operacional());
        assertEquals(bd("120.00"), r.pedagios());
        assertEquals(bd("2502.08"), r.custoTotal());
        assertEquals(bd("375.31"), r.margem());
        assertEquals(bd("2877.39"), r.frete());
        assertEquals(bd("5.75"), r.fretePorKm());
        assertEquals(bd("75.72"), r.fretePorTonelada());
    }

    @Test
    void casoB_carretaVazia() {
        var r = calculadora.calcular(new ParametrosFrete(TipoCaminhao.CARRETA_30T, 0.0, 300.0,
                bd("6.20"), BigDecimal.ZERO, bd("2.00"), bd("10")));
        assertEquals(3.15, r.consumoKmL(), 1e-9);
        assertEquals(bd("590.48"), r.combustivel());
        assertEquals(bd("600.00"), r.operacional());
        assertEquals(bd("1190.48"), r.custoTotal());
        assertEquals(bd("119.05"), r.margem());
        assertEquals(bd("1309.53"), r.frete());
        assertEquals(bd("4.37"), r.fretePorKm());
        assertNull(r.fretePorTonelada());
    }

    @Test
    void casoC_rodotremCarregado() {
        var r = calculadora.calcular(new ParametrosFrete(TipoCaminhao.RODOTREM, 48.0, 1000.0,
                bd("6.00"), bd("350.00"), bd("3.00"), bd("12")));
        assertEquals(bd("2790.70"), r.combustivel());
        assertEquals(bd("6140.70"), r.custoTotal());
        assertEquals(bd("736.88"), r.margem());
        assertEquals(bd("6877.58"), r.frete());
        assertEquals(bd("6.88"), r.fretePorKm());
        assertEquals(bd("143.28"), r.fretePorTonelada());
    }

    @Test
    void pisoAcimaDoFreteSinaliza() {
        assertTrue(calculadora.calcular(casoA(bd("3000.00"))).abaixoDoPiso());
    }

    @Test
    void pisoIgualAoFreteNaoSinaliza() {
        assertFalse(calculadora.calcular(casoA(bd("2877.39"))).abaixoDoPiso());
    }

    @Test
    void semPisoNaoSinaliza() {
        assertFalse(calculadora.calcular(casoA(null)).abaixoDoPiso());
    }

    @Test
    void margemZeroDeixaFreteIgualAoCusto() {
        var r = calculadora.calcular(new ParametrosFrete(TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0,
                bd("6.00"), bd("120.00"), bd("2.50"), BigDecimal.ZERO));
        assertEquals(r.custoTotal(), r.frete());
        assertEquals(bd("0.00"), r.margem());
    }

    @Test
    void cargaAcimaDaCapacidadeELancada() {
        assertThrows(IllegalArgumentException.class, () -> calculadora.calcular(
                new ParametrosFrete(TipoCaminhao.CARRETA_4_EIXOS, 38.1, 500.0,
                        bd("6.00"), bd("120.00"), bd("2.50"), bd("15"))));
    }

    @Test
    void validaParametrosObrigatorios() {
        assertThrows(IllegalArgumentException.class, () -> new ParametrosFrete(
                TipoCaminhao.CARRETA_4_EIXOS, 38.0, 0.0, bd("6.00"), bd("120.00"), bd("2.50"), bd("15")));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosFrete(
                TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0, BigDecimal.ZERO, bd("120.00"), bd("2.50"), bd("15")));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosFrete(
                TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0, bd("6.00"), bd("-1"), bd("2.50"), bd("15")));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosFrete(
                TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0, bd("6.00"), bd("120.00"), bd("-0.01"), bd("15")));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosFrete(
                TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0, bd("6.00"), bd("120.00"), bd("2.50"), bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosFrete(
                TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0, bd("6.00"), bd("120.00"), bd("2.50"), bd("15"), bd("-1")));
    }
}
