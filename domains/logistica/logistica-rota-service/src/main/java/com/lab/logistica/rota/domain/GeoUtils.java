package com.lab.logistica.rota.domain;

/** Distância geodésica (grande círculo), sem dependência de rede -- ver ProvedorDistancias. */
public final class GeoUtils {

    private static final double RAIO_TERRA_KM = 6371.0088;

    private GeoUtils() {
    }

    /** Distância em linha reta sobre a esfera, em km. Subestima distância rodoviária real. */
    public static double haversineKm(Coordenada a, Coordenada b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
