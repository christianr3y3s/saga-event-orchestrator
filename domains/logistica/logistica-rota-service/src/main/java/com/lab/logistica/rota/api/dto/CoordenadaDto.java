package com.lab.logistica.rota.api.dto;

import com.lab.logistica.rota.domain.Coordenada;

public record CoordenadaDto(double lat, double lng) {

    public Coordenada paraDominio() {
        return new Coordenada(lat, lng);
    }

    public static CoordenadaDto deDominio(Coordenada c) {
        return new CoordenadaDto(c.lat(), c.lng());
    }
}
