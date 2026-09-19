package com.lab.logistica.rota.api;

import com.lab.logistica.rota.api.dto.CalcularFreteRequest;
import com.lab.logistica.rota.api.dto.CalcularFreteResponse;
import com.lab.logistica.rota.domain.CalculadoraFrete;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cálculo de frete (combustível + operacional + pedágios + margem), separado de
 * {@link RotaController} por SRP -- é uma responsabilidade independente da simulação de
 * rota, mesmo consumindo o resultado dela como entrada em fluxos futuros. Ver
 * domains/logistica/README.md para a composição do frete e as premissas.
 */
@RestController
public class FreteController {

    private final CalculadoraFrete calculadoraFrete;

    public FreteController(CalculadoraFrete calculadoraFrete) {
        this.calculadoraFrete = calculadoraFrete;
    }

    @PostMapping("/fretes/calcular")
    public CalcularFreteResponse calcularFrete(@RequestBody CalcularFreteRequest request) {
        var resultado = calculadoraFrete.calcular(request.paraDominio());
        return CalcularFreteResponse.deDominio(resultado);
    }
}
