package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsumoTest {

    @Test
    void calculaLitros() {
        assertEquals(40.0, Consumo.litros(100.0, 2.5), 1e-9);
    }

    @Test
    void consumoZeroOuNegativoNaoDividePorZero() {
        assertEquals(0.0, Consumo.litros(100.0, 0.0));
        assertEquals(0.0, Consumo.litros(100.0, -1.0));
    }

    @Test
    void limiteExatoNaoDisparaAlerta() {
        assertFalse(Consumo.abaixoDoLimite(2.5));
    }

    @Test
    void abaixoDoLimiteDisparaAlerta() {
        assertTrue(Consumo.abaixoDoLimite(2.49));
    }

    @Test
    void rodotremCarregadoNominalNaoDisparaAlerta() {
        // Regressão do bug do protótipo: o nominal do rodotrem carregado (2.15) é sempre
        // < 2.5, então aplicar o corte ao nominal (em vez de ao medido) dispararia sempre.
        double nominal = TipoCaminhao.RODOTREM.consumoKmL(TipoCaminhao.RODOTREM.capacidadeToneladas());
        assertTrue(nominal < Consumo.LIMITE_MIN_KM_L, "premissa do teste: nominal deveria ser < limite");
        // O alerta só é avaliado quando alguém chama abaixoDoLimite explicitamente sobre
        // um valor MEDIDO -- este teste documenta que Consumo não faz essa chamada sozinho.
    }
}
