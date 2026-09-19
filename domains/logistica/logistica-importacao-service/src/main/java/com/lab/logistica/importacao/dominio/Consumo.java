package com.lab.logistica.importacao.dominio;

/**
 * Mesmo limite de controle de logistica-rota-service.domain.Consumo (2,5 km/L, aplicado
 * só ao consumo MEDIDO). Duplicado aqui pela mesma convenção de isolamento entre módulos
 * -- ver ModelosCaminhaoConhecidos.
 */
final class Consumo {
    static final double LIMITE_MIN_KM_L = 2.5;

    private Consumo() {
    }
}
