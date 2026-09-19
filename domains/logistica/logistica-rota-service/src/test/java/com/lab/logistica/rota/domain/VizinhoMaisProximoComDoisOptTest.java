package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VizinhoMaisProximoComDoisOptTest {

    private final SolverRota solver = new VizinhoMaisProximoComDoisOpt();

    // 0=(0,0) 1=(1,1) 2=(1,0) 3=(0,1); a ordem 0,1,2,3 cruza as diagonais do quadrado.
    private final double[][] quadrado = {
            {0.0, Math.sqrt(2.0), 1.0, 1.0},
            {Math.sqrt(2.0), 0.0, 1.0, 1.0},
            {1.0, 1.0, 0.0, Math.sqrt(2.0)},
            {1.0, 1.0, Math.sqrt(2.0), 0.0}
    };

    @Test
    void matrizVaziaGeraRotaVazia() {
        assertEquals(List.of(), solver.resolver(new double[0][0]));
    }

    @Test
    void umPontoGeraRotaComApenasOrigem() {
        assertEquals(List.of(0), solver.resolver(new double[][]{{0.0}}));
    }

    @Test
    void doisPontosIdaEVolta() {
        double[][] m = {{0.0, 5.0}, {5.0, 0.0}};
        assertEquals(List.of(0, 1, 0), solver.resolver(m));
    }

    @Test
    void rejeitaMatrizNaoQuadrada() {
        double[][] m = {{0.0, 1.0}, {1.0}};
        assertThrows(IllegalArgumentException.class, () -> solver.resolver(m));
    }

    @Test
    void doisOptDesfazCruzamento() {
        List<Integer> cruzada = List.of(0, 1, 2, 3);
        double custoAntes = VizinhoMaisProximoComDoisOpt.custoFechado(quadrado, cruzada);
        List<Integer> melhorada = VizinhoMaisProximoComDoisOpt.doisOpt(quadrado, cruzada);
        assertTrue(VizinhoMaisProximoComDoisOpt.custoFechado(quadrado, melhorada) < custoAntes);
        assertEquals(4.0, VizinhoMaisProximoComDoisOpt.custoFechado(quadrado, melhorada), 1e-9);
    }

    @Test
    void doisOptMantemRotaJaOtima() {
        List<Integer> otima = List.of(0, 2, 1, 3);
        assertEquals(otima, VizinhoMaisProximoComDoisOpt.doisOpt(quadrado, otima));
    }

    @Test
    void resolverNoQuadradoAcha4() {
        List<Integer> rota = solver.resolver(quadrado);
        double custo = 0.0;
        for (int i = 0; i + 1 < rota.size(); i++) {
            custo += quadrado[rota.get(i)][rota.get(i + 1)];
        }
        assertEquals(4.0, custo, 1e-9);
    }

    @Test
    void rotaEPermutacaoFechadaNaOrigemENaoPiorQueVizinhoMaisProximo() {
        Random rnd = new Random(42);
        int n = 8;
        List<Coordenada> pts = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new Coordenada(-23.0 + rnd.nextDouble(), -46.0 + rnd.nextDouble()));
        }
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m[i][j] = GeoUtils.haversineKm(pts.get(i), pts.get(j));
            }
        }

        List<Integer> rota = solver.resolver(m);

        assertEquals(0, rota.get(0));
        assertEquals(0, rota.get(rota.size() - 1));
        List<Integer> semRepeticao = new java.util.ArrayList<>(rota.subList(0, rota.size() - 1));
        java.util.Collections.sort(semRepeticao);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), semRepeticao);

        double custoFinal = VizinhoMaisProximoComDoisOpt.custoFechado(m, rota.subList(0, rota.size() - 1));
        double custoNN = VizinhoMaisProximoComDoisOpt.custoFechado(m, VizinhoMaisProximoComDoisOpt.vizinhoMaisProximo(m));
        assertTrue(custoFinal <= custoNN + 1e-9);
    }

    @Test
    void funcionaComMatrizAssimetrica() {
        double[][] m = {
                {0.0, 1.0, 10.0},
                {10.0, 0.0, 1.0},
                {1.0, 10.0, 0.0}
        };
        assertEquals(List.of(0, 1, 2, 0), solver.resolver(m));
    }
}
