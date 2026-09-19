package com.lab.logistica.rota.domain;

/**
 * Observação de controle da tabela de modelos: consumo MEDIDO abaixo de 2,5 km/L pode
 * indicar perda de eficiência ou desvio de combustível. Deliberadamente NÃO se aplica ao
 * consumo nominal da tabela -- o rodotrem carregado tem nominal de 2,15 km/L e nunca
 * deveria disparar o alerta só por existir.
 */
public final class Consumo {

    public static final double LIMITE_MIN_KM_L = 2.5;

    private Consumo() {
    }

    public static double litros(double distanciaKm, double kmL) {
        return kmL > 0 ? distanciaKm / kmL : 0.0;
    }

    public static boolean abaixoDoLimite(double kmLMedido) {
        return kmLMedido < LIMITE_MIN_KM_L;
    }
}
