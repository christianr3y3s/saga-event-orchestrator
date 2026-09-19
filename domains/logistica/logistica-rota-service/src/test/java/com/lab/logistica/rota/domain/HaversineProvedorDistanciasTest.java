package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class HaversineProvedorDistanciasTest {

    private final List<Coordenada> pontos = List.of(
            new Coordenada(-23.55, -46.63),
            new Coordenada(-22.90, -43.17),
            new Coordenada(-19.92, -43.94)
    );

    @Test
    void diagonalZeraESimetria() {
        double[][] m = new HaversineProvedorDistancias(1.0).matrizKm(pontos);
        for (int i = 0; i < pontos.size(); i++) {
            assertEquals(0.0, m[i][i]);
            for (int j = 0; j < pontos.size(); j++) {
                assertEquals(m[i][j], m[j][i], 1e-9);
            }
        }
    }

    @Test
    void fatorRodoviarioMultiplicaADistancia() {
        double[][] base = new HaversineProvedorDistancias(1.0).matrizKm(pontos);
        double[][] fator = new HaversineProvedorDistancias(1.3).matrizKm(pontos);
        assertEquals(base[0][1] * 1.3, fator[0][1], 1e-9);
    }

    @Test
    void rejeitaFatorMenorQueUm() {
        assertThrows(IllegalArgumentException.class, () -> new HaversineProvedorDistancias(0.9));
    }

    @Test
    void listaVaziaGeraMatrizVazia() {
        assertEquals(0, new HaversineProvedorDistancias(1.0).matrizKm(List.of()).length);
    }
}
