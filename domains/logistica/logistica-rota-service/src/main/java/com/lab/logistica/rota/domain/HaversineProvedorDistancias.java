package com.lab.logistica.rota.domain;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementação de referência: distância em linha reta multiplicada por um fator fixo de
 * "sinuosidade" rodoviária. Rápida e sem dependência externa, mas não é rota rodoviária
 * real -- não sabe de restrição de peso/altura, posto de pesagem nem praça de pedágio. É o
 * fallback padrão (quando {@code app.rota.distancia-provider} não é definido ou vale
 * exatamente {@code "haversine"}); para distância real, ative
 * {@link OpenRouteServiceProvedorDistancias} com {@code app.rota.distancia-provider=openrouteservice}.
 * Trocar de provedor é só configuração -- as duas implementam a mesma interface (DIP), então
 * qual delas o Spring injeta em {@code SimuladorRota} depende só dessa propriedade.
 */
@Component
@ConditionalOnProperty(name = "app.rota.distancia-provider", havingValue = "haversine", matchIfMissing = true)
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
