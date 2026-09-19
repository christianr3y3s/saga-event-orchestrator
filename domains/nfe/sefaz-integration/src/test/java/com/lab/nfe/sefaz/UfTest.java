package com.lab.nfe.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UfTest {

    @Test
    void codigosIbgeDasUfsMencionadasNesteProjeto() {
        assertEquals(15, Uf.PA.codigoIbge());
        assertEquals(35, Uf.SP.codigoIbge());
    }

    @Test
    void todasAs27UfsEstaoPresentes() {
        assertEquals(27, Uf.values().length);
    }
}
