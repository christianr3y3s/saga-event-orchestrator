package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    private final Coordenada sp = new Coordenada(-23.5505, -46.6333);
    private final Coordenada rj = new Coordenada(-22.9068, -43.1729);

    @Test
    void mesmoPontoTemDistanciaZero() {
        assertEquals(0.0, GeoUtils.haversineKm(sp, sp), 1e-9);
    }

    @Test
    void umGrauNoEquadorEquivaleA111km() {
        assertEquals(111.195, GeoUtils.haversineKm(new Coordenada(0.0, 0.0), new Coordenada(0.0, 1.0)), 0.01);
    }

    @Test
    void saoPauloRioEstaNaFaixaEsperada() {
        double d = GeoUtils.haversineKm(sp, rj);
        assertTrue(d >= 350.0 && d <= 365.0, "distância obtida: " + d);
    }

    @Test
    void eSimetrica() {
        assertEquals(GeoUtils.haversineKm(sp, rj), GeoUtils.haversineKm(rj, sp), 1e-9);
    }
}
