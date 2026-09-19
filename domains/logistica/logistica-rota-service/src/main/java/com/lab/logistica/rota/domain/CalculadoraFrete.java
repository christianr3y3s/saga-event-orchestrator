package com.lab.logistica.rota.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Ver ParametrosFrete para as regras. Valores monetários em BigDecimal, HALF_UP em cada linha. */
@Component
public class CalculadoraFrete {

    private static BigDecimal r2(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public ResultadoFrete calcular(ParametrosFrete p) {
        double consumoKmL = p.caminhao().consumoKmL(p.cargaToneladas());
        double litros = p.distanciaKm() / consumoKmL;

        BigDecimal combustivel = r2(BigDecimal.valueOf(litros).multiply(p.precoDieselPorLitro()));
        BigDecimal operacional = r2(BigDecimal.valueOf(p.distanciaKm()).multiply(p.custoOperacionalPorKm()));
        BigDecimal pedagios = r2(p.pedagios());
        BigDecimal custoTotal = combustivel.add(operacional).add(pedagios);
        BigDecimal margem = custoTotal.multiply(p.margemPercentual())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal frete = custoTotal.add(margem);

        BigDecimal fretePorKm = frete.divide(BigDecimal.valueOf(p.distanciaKm()), 2, RoundingMode.HALF_UP);
        BigDecimal fretePorTonelada = p.cargaToneladas() > 0
                ? frete.divide(BigDecimal.valueOf(p.cargaToneladas()), 2, RoundingMode.HALF_UP)
                : null;
        boolean abaixoDoPiso = p.pisoMinimo() != null && frete.compareTo(p.pisoMinimo()) < 0;

        return new ResultadoFrete(consumoKmL, litros, combustivel, operacional, pedagios,
                custoTotal, margem, frete, fretePorKm, fretePorTonelada, abaixoDoPiso);
    }
}
