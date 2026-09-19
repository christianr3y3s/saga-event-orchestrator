package com.lab.logistica.rota.api;

import com.lab.logistica.rota.api.dto.CalcularFreteRequest;
import com.lab.logistica.rota.api.dto.CalcularFreteResponse;
import com.lab.logistica.rota.api.dto.CoordenadaDto;
import com.lab.logistica.rota.api.dto.SimularRotaRequest;
import com.lab.logistica.rota.api.dto.SimularRotaResponse;
import com.lab.logistica.rota.domain.CalculadoraFrete;
import com.lab.logistica.rota.domain.SimuladorRota;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Motor de otimização de rota (peça 1 de domains/logistica/DESIGN.md) exposto como API
 * síncrona -- não é um participante da saga (sem tópico Kafka, sem FSM). Ver
 * domains/logistica/README.md para as premissas e limitações de cada endpoint.
 */
@RestController
public class RotaController {

    private final SimuladorRota simuladorRota;
    private final CalculadoraFrete calculadoraFrete;

    public RotaController(SimuladorRota simuladorRota, CalculadoraFrete calculadoraFrete) {
        this.simuladorRota = simuladorRota;
        this.calculadoraFrete = calculadoraFrete;
    }

    @PostMapping("/rotas/simular")
    public SimularRotaResponse simularRota(@RequestBody SimularRotaRequest request) {
        var pontos = request.pontos().stream().map(CoordenadaDto::paraDominio).toList();
        var resultado = simuladorRota.simular(pontos, request.caminhao(), request.cargaToneladas());
        return SimularRotaResponse.deDominio(resultado);
    }

    @PostMapping("/fretes/calcular")
    public CalcularFreteResponse calcularFrete(@RequestBody CalcularFreteRequest request) {
        var resultado = calculadoraFrete.calcular(request.paraDominio());
        return CalcularFreteResponse.deDominio(resultado);
    }
}
