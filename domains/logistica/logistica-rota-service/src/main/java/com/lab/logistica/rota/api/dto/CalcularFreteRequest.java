package com.lab.logistica.rota.api.dto;

import com.lab.logistica.rota.domain.ParametrosFrete;
import com.lab.logistica.rota.domain.TipoCaminhao;
import java.math.BigDecimal;

public record CalcularFreteRequest(
        TipoCaminhao caminhao,
        double cargaToneladas,
        double distanciaKm,
        BigDecimal precoDieselPorLitro,
        BigDecimal pedagios,
        BigDecimal custoOperacionalPorKm,
        BigDecimal margemPercentual,
        BigDecimal pisoMinimo
) {
    public ParametrosFrete paraDominio() {
        return new ParametrosFrete(caminhao, cargaToneladas, distanciaKm, precoDieselPorLitro,
                pedagios, custoOperacionalPorKm, margemPercentual, pisoMinimo);
    }
}
