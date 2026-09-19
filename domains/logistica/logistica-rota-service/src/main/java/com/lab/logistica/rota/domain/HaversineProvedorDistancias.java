package com.lab.logistica.rota.domain;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementação de referência: distância em linha reta multiplicada por um fator fixo de
 * "sinuosidade" rodoviária. É a peça marcada como "dados de referência agora, dados reais
 * depois" em DESIGN.md -- não tem perfil de peso/altura de caminhão pesado nem evita
 * restrições de via. Troque por OpenRouteService/GraphHopper (perfil driving-hgv) antes de
 * usar em decisão real de rota de carga pesada.
 */
@Component
public class HaversineProvedorDistancias implements ProvedorDistancias {

    private final double fatorRodoviario;

    public HaversineProvedorDistancias(@Value("${app.rota.fator-rodoviario:1.0}") double fatorRodoviario) {
        if (fatorRodoviario < 1.0) {
            throw new IllegalArgumentException("fatorRodoviario deve ser >= 1.0, recebido: " + fatorRodoviario);
        }
        this.fatorRodoviario = fatorRodoviario;
    }

    @Override
    public double[][] matrizKm(List<Coordenada> pontos) {
        int n = pontos.size();
        double[][] matriz = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matriz[i][j] = (i == j) ? 0.0 : GeoUtils.haversineKm(pontos.get(i), pontos.get(j)) * fatorRodoviario;
            }
        }
        return matriz;
    }
}
