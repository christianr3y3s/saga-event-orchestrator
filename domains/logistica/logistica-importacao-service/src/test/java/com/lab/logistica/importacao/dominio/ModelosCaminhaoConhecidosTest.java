package com.lab.logistica.importacao.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelosCaminhaoConhecidosTest {

    @Test
    void reconheceOsTresModelos() {
        assertTrue(ModelosCaminhaoConhecidos.ehValido("RODOTREM"));
        assertTrue(ModelosCaminhaoConhecidos.ehValido("CARRETA_4_EIXOS"));
        assertTrue(ModelosCaminhaoConhecidos.ehValido("CARRETA_30T"));
    }

    @Test
    void rejeitaDesconhecido() {
        assertFalse(ModelosCaminhaoConhecidos.ehValido("BITREM"));
        assertFalse(ModelosCaminhaoConhecidos.ehValido(null));
    }

    @Test
    void consumoNominalConfereComATabelaValidada() {
        assertEquals(2.85, ModelosCaminhaoConhecidos.consumoNominalKmL("RODOTREM", 0.0), 1e-9);
        assertEquals(2.15, ModelosCaminhaoConhecidos.consumoNominalKmL("RODOTREM", 48.0), 1e-9);
    }

    @Test
    void lancaParaModeloDesconhecido() {
        assertThrows(IllegalArgumentException.class, () -> ModelosCaminhaoConhecidos.consumoNominalKmL("BITREM", 10.0));
    }
}
