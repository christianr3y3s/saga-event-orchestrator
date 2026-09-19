package com.lab.logistica.rota.domain;

import java.util.List;

/**
 * Camada de solver (DESIGN.md, camada 3): dado a matriz de distâncias, devolve a ordem de
 * visita. Interface, não classe estática -- para que SimuladorRota dependa de uma
 * abstração (DIP) e trocar de algoritmo seja injetar outro bean, não editar
 * SimuladorRota. Antes desta versão, TspSolver era uma classe final só com métodos
 * estáticos chamada diretamente por SimuladorRota: a Javadoc dizia "plugável", mas nada
 * impedia trocar de algoritmo sem mexer em SimuladorRota -- esta interface fecha essa
 * lacuna entre o que estava documentado e o que o código realmente permitia.
 */
public interface SolverRota {

    /** Circuito fechado começando e terminando no índice 0. */
    List<Integer> resolver(double[][] matrizDistancias);
}
