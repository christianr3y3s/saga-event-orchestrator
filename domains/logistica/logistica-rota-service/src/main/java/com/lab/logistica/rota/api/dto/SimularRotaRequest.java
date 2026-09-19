package com.lab.logistica.rota.api.dto;

import com.lab.logistica.rota.domain.TipoCaminhao;
import java.util.List;

public record SimularRotaRequest(
        TipoCaminhao caminhao,
        double cargaToneladas,
        List<CoordenadaDto> pontos
) {
}
