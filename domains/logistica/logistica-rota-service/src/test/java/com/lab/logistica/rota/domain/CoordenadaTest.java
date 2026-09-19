package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CoordenadaTest {

    @Test
    void aceitaLimites() {
        assertEquals(90.0, new Coordenada(90.0, 180.0).lat());
        assertEquals(-180.0, new Coordenada(-90.0, -180.0).lng());
    }

    @Test
    void rejeitaLatitudeInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new Coordenada(90.1, 0.0));
    }

    @Test
    void rejeitaLongitudeInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new Coordenada(0.0, -180.1));
    }
}
