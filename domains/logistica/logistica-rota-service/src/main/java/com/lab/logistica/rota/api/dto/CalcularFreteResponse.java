package com.lab.logistica.rota.api.dto;

import com.lab.logistica.rota.domain.ResultadoFrete;
import java.math.BigDecimal;

public record CalcularFreteResponse(
        double consumoKmL,
        double litros,
        BigDecimal combustivel,
        BigDecimal operacional,
        BigDecimal pedagios,
        BigDecimal custoTotal,
        BigDecimal margem,
        BigDecimal frete,
        BigDecimal fretePorKm,
        BigDecimal fretePorTonelada,
        boolean abaixoDoPiso
) {
    public static CalcularFreteResponse deDominio(ResultadoFrete r) {
        return new CalcularFreteResponse(r.consumoKmL(), r.litros(), r.combustivel(), r.operacional(),
                r.pedagios(), r.custoTotal(), r.margem(), r.frete(), r.fretePorKm(), r.fretePorTonelada(),
                r.abaixoDoPiso());
    }
}
