package com.lab.logistica.rota.domain;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Orquestra as três primeiras camadas de DESIGN.md (distância -> matriz -> solver) e
 * aplica o consumo do modelo de caminhão sobre o resultado. Não conhece Kafka nem saga --
 * é chamado de forma síncrona pela API (ver RotaController), do mesmo jeito que
 * CashbackCalculator é chamado por dentro do consumer de cashback-service.
 */
@Component
public class SimuladorRota {

    private final ProvedorDistancias provedor;

    public SimuladorRota(ProvedorDistancias provedor) {
        this.provedor = provedor;
    }

    public ResultadoRota simular(List<Coordenada> pontos, TipoCaminhao caminhao, double cargaToneladas) {
        if (pontos.size() < 2) {
            throw new IllegalArgumentException("São necessários ao menos 2 pontos");
        }
        double[][] matriz = provedor.matrizKm(pontos);
        List<Integer> ordemIndices = TspSolver.resolver(matriz);

        double distancia = 0.0;
        for (int i = 0; i + 1 < ordemIndices.size(); i++) {
            distancia += matriz[ordemIndices.get(i)][ordemIndices.get(i + 1)];
        }

        double kmL = caminhao.consumoKmL(cargaToneladas);
        List<Coordenada> ordemPontos = ordemIndices.stream().map(pontos::get).toList();

        return new ResultadoRota(ordemPontos, distancia, kmL, Consumo.litros(distancia, kmL));
    }
}
