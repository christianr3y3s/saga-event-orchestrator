package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TipoCaminhaoTest {

    @Test
    void tabelaConfereComOsValoresValidados() {
        assertEquals(48.0, TipoCaminhao.RODOTREM.capacidadeToneladas());
        assertEquals(2.85, TipoCaminhao.RODOTREM.kmLVazio());
        assertEquals(2.15, TipoCaminhao.RODOTREM.kmLCarregado());

        assertEquals(38.0, TipoCaminhao.CARRETA_4_EIXOS.capacidadeToneladas());
        assertEquals(3.15, TipoCaminhao.CARRETA_4_EIXOS.kmLVazio());
        assertEquals(2.65, TipoCaminhao.CARRETA_4_EIXOS.kmLCarregado());

        assertEquals(30.0, TipoCaminhao.CARRETA_30T.capacidadeToneladas());
        assertEquals(3.15, TipoCaminhao.CARRETA_30T.kmLVazio());
        assertEquals(2.85, TipoCaminhao.CARRETA_30T.kmLCarregado());
    }

    @Test
    void vazioUsaConsumoDeVazio() {
        for (TipoCaminhao t : TipoCaminhao.values()) {
            assertEquals(t.kmLVazio(), t.consumoKmL(0.0), 1e-9);
        }
    }

    @Test
    void cargaMaximaUsaConsumoDeCarregado() {
        for (TipoCaminhao t : TipoCaminhao.values()) {
            assertEquals(t.kmLCarregado(), t.consumoKmL(t.capacidadeToneladas()), 1e-9);
        }
    }

    @Test
    void metadeDaCargaInterpolaAoMeio() {
        TipoCaminhao c = TipoCaminhao.CARRETA_30T;
        assertEquals((c.kmLVazio() + c.kmLCarregado()) / 2, c.consumoKmL(15.0), 1e-9);
    }

    @Test
    void rejeitaCargaAcimaDaCapacidade() {
        assertThrows(IllegalArgumentException.class, () -> TipoCaminhao.CARRETA_30T.consumoKmL(30.1));
    }

    @Test
    void rejeitaCargaNegativa() {
        assertThrows(IllegalArgumentException.class, () -> TipoCaminhao.RODOTREM.consumoKmL(-1.0));
    }

    @Test
    void rejeitaCargaNaN() {
        assertThrows(IllegalArgumentException.class, () -> TipoCaminhao.RODOTREM.consumoKmL(Double.NaN));
    }

    @Test
    void expoeNomeDeExibicao() {
        assertEquals("Carreta 4 Eixos", TipoCaminhao.CARRETA_4_EIXOS.nomeExibicao());
    }
}
