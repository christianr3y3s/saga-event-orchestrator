package com.lab.logistica.rota.api.dto;

import com.lab.logistica.rota.domain.ResultadoRota;
import java.util.List;

public record SimularRotaResponse(
        List<CoordenadaDto> ordem,
        double distanciaKm,
        double consumoKmL,
        double litros
) {
    public static SimularRotaResponse deDominio(ResultadoRota r) {
        return new SimularRotaResponse(
                r.ordem().stream().map(CoordenadaDto::deDominio).toList(),
                r.distanciaKm(), r.consumoKmL(), r.litros());
    }
}
