package com.lab.logistica.rota.domain;

import java.util.List;

/**
 * Camada de matriz de distância (DESIGN.md, camada 2): dado N pontos, devolve a matriz
 * NxN de distância em km entre todos os pares. Plugável -- a implementação de referência
 * (HaversineProvedorDistancias) é em linha reta; para produção, troque por uma
 * implementação sobre OpenRouteService/GraphHopper (perfil de veículo pesado), conforme a
 * comparação em DESIGN.md.
 */
public interface ProvedorDistancias {

    double[][] matrizKm(List<Coordenada> pontos);
}
