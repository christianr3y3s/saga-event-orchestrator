package com.lab.logistica.rota.api;

import com.lab.logistica.rota.api.dto.CoordenadaDto;
import com.lab.logistica.rota.api.dto.SimularRotaRequest;
import com.lab.logistica.rota.api.dto.SimularRotaResponse;
import com.lab.logistica.rota.domain.SimuladorRota;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Motor de otimização de rota (peça 1 de domains/logistica/DESIGN.md) exposto como API
 * síncrona -- não é um participante da saga (sem tópico Kafka, sem FSM). Ver
 * domains/logistica/README.md para as premissas e limitações do endpoint.
 *
 * <p>Só cuida de simulação de rota (SRP) -- cálculo de frete é uma responsabilidade
 * separada e mora em {@link FreteController}, mesmo os dois vivendo sob o mesmo módulo e
 * porta; antes, um único {@code RotaController} respondia pelos dois, o que misturava duas
 * razões de mudança (mudar a simulação de rota não deveria arriscar quebrar o cálculo de
 * frete, e vice-versa).
 */
@RestController
public class RotaController {

    private final SimuladorRota simuladorRota;

    public RotaController(SimuladorRota simuladorRota) {
        this.simuladorRota = simuladorRota;
    }

    @PostMapping("/rotas/simular")
    public SimularRotaResponse simularRota(@RequestBody SimularRotaRequest request) {
        var pontos = request.pontos().stream().map(CoordenadaDto::paraDominio).toList();
        var resultado = simuladorRota.simular(pontos, request.caminhao(), request.cargaToneladas());
        return SimularRotaResponse.deDominio(resultado);
    }
}
