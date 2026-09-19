package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimuladorRotaTest {

    private static final class MatrizFixa implements ProvedorDistancias {
        private final double km;

        MatrizFixa(double km) {
            this.km = km;
        }

        @Override
        public double[][] matrizKm(List<Coordenada> pontos) {
            int n = pontos.size();
            double[][] m = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    m[i][j] = (i == j) ? 0.0 : km;
                }
            }
            return m;
        }
    }

    /** Solver falso: devolve sempre a ordem identidade (0,1,2,...,0), sem otimizar nada. */
    private static final class SolverIdentidade implements SolverRota {
        private boolean chamado = false;

        @Override
        public List<Integer> resolver(double[][] matriz) {
            chamado = true;
            List<Integer> ordem = new java.util.ArrayList<>();
            for (int i = 0; i < matriz.length; i++) {
                ordem.add(i);
            }
            if (!ordem.isEmpty()) {
                ordem.add(ordem.get(0));
            }
            return ordem;
        }
    }

    private final SolverRota solverPadrao = new VizinhoMaisProximoComDoisOpt();

    private final List<Coordenada> tres = List.of(
            new Coordenada(0.0, 0.0), new Coordenada(0.0, 1.0), new Coordenada(1.0, 1.0));

    @Test
    void somaDistanciaDoCircuitoECalculaLitros() {
        var sim = new SimuladorRota(new MatrizFixa(10.0), solverPadrao);
        var r = sim.simular(tres, TipoCaminhao.CARRETA_30T, 30.0);

        assertEquals(30.0, r.distanciaKm(), 1e-9);
        assertEquals(2.85, r.consumoKmL(), 1e-9);
        assertEquals(30.0 / 2.85, r.litros(), 1e-9);
        assertEquals(4, r.ordem().size());
        assertEquals(r.ordem().get(0), r.ordem().get(3));
    }

    @Test
    void veiculoVazioConsomeMenos() {
        var sim = new SimuladorRota(new MatrizFixa(10.0), solverPadrao);
        var vazio = sim.simular(tres, TipoCaminhao.RODOTREM, 0.0);
        var cheio = sim.simular(tres, TipoCaminhao.RODOTREM, 48.0);
        assertTrue(vazio.litros() < cheio.litros());
    }

    @Test
    void exigeAoMenosDoisPontos() {
        var sim = new SimuladorRota(new MatrizFixa(1.0), solverPadrao);
        assertThrows(IllegalArgumentException.class, () -> sim.simular(tres.subList(0, 1), TipoCaminhao.RODOTREM, 0.0));
    }

    @Test
    void propagaExcecaoDeCargaInvalida() {
        var sim = new SimuladorRota(new MatrizFixa(1.0), solverPadrao);
        assertThrows(IllegalArgumentException.class, () -> sim.simular(tres, TipoCaminhao.CARRETA_30T, 31.0));
    }

    @Test
    void delegaAoSolverInjetadoEmVezDeUmaImplementacaoFixa() {
        // Prova a troca de algoritmo via DI (DIP): um SolverRota completamente diferente
        // (aqui, um "solver" trivial que não otimiza nada) é usado sem qualquer mudança em
        // SimuladorRota -- só trocando o que é passado no construtor.
        var solverFalso = new SolverIdentidade();
        var sim = new SimuladorRota(new MatrizFixa(7.0), solverFalso);

        var r = sim.simular(tres, TipoCaminhao.RODOTREM, 0.0);

        assertTrue(solverFalso.chamado);
        assertEquals(21.0, r.distanciaKm(), 1e-9);
    }
}
