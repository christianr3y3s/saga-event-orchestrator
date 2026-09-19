package com.lab.logistica.rota.domain;

/** Latitude/longitude de um ponto da rota. Validada na borda para nunca circular inválida internamente. */
public record Coordenada(double lat, double lng) {

    public Coordenada {
        if (Double.isNaN(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude inválida: " + lat);
        }
        if (Double.isNaN(lng) || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude inválida: " + lng);
        }
    }
}
