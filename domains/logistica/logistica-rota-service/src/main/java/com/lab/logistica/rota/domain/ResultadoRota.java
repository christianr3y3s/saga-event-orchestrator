package com.lab.logistica.rota.domain;

import java.util.List;

public record ResultadoRota(
        List<Coordenada> ordem,
        double distanciaKm,
        double consumoKmL,
        double litros
) {
}
