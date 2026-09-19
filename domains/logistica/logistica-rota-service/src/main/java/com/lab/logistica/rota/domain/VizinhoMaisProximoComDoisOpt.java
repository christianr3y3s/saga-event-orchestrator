package com.lab.logistica.rota.domain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implementação de referência de {@link SolverRota}: heurística de vizinho mais próximo
 * seguida de 2-opt. Adequada para o número de paradas de uma rota de entrega (dezenas, não
 * milhares de pontos). É um {@code @Component} para que trocar de algoritmo seja trocar o
 * bean injetado em {@link SimuladorRota}, não editar o código de {@code SimuladorRota} --
 * a mesma forma como {@link HaversineProvedorDistancias} é injetado como
 * {@link ProvedorDistancias}.
 */
@Component
public class VizinhoMaisProximoComDoisOpt implements SolverRota {

    @Override
    public List<Integer> resolver(double[][] matriz) {
        int n = matriz.length;
        for (double[] linha : matriz) {
            if (linha.length != n) {
                throw new IllegalArgumentException("A matriz de distâncias deve ser quadrada");
            }
        }
        if (n == 0) {
            return List.of();
        }
        if (n == 1) {
            return List.of(0);
        }
        List<Integer> rota = doisOpt(matriz, vizinhoMaisProximo(matriz));
        List<Integer> circuito = new ArrayList<>(rota);
        circuito.add(rota.get(0));
        return circuito;
    }

    /** Custo do circuito fechado para uma rota aberta (sem repetir o ponto inicial). */
    static double custoFechado(double[][] matriz, List<Integer> rota) {
        double custo = 0.0;
        int n = rota.size();
        for (int i = 0; i < n; i++) {
            custo += matriz[rota.get(i)][rota.get((i + 1) % n)];
        }
        return custo;
    }

    static List<Integer> vizinhoMaisProximo(double[][] matriz) {
        int n = matriz.length;
        boolean[] visitado = new boolean[n];
        List<Integer> rota = new ArrayList<>();
        rota.add(0);
        visitado[0] = true;
        while (rota.size() < n) {
            int atual = rota.get(rota.size() - 1);
            int proximo = -1;
            double melhorDist = Double.MAX_VALUE;
            for (int candidato = 0; candidato < n; candidato++) {
                if (!visitado[candidato] && matriz[atual][candidato] < melhorDist) {
                    melhorDist = matriz[atual][candidato];
                    proximo = candidato;
                }
            }
            visitado[proximo] = true;
            rota.add(proximo);
        }
        return rota;
    }

    static List<Integer> doisOpt(double[][] matriz, List<Integer> inicial) {
        int n = inicial.size();
        List<Integer> melhor = new ArrayList<>(inicial);
        double custoAtual = custoFechado(matriz, melhor);
        boolean melhorou = true;
        while (melhorou) {
            melhorou = false;
            for (int i = 1; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    List<Integer> candidata = new ArrayList<>(melhor);
                    reverter(candidata, i, j);
                    double custo = custoFechado(matriz, candidata);
                    if (custo < custoAtual - 1e-9) {
                        melhor = candidata;
                        custoAtual = custo;
                        melhorou = true;
                    }
                }
            }
        }
        return melhor;
    }

    private static void reverter(List<Integer> lista, int i, int j) {
        while (i < j) {
            Integer tmp = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, tmp);
            i++;
            j--;
        }
    }
}
