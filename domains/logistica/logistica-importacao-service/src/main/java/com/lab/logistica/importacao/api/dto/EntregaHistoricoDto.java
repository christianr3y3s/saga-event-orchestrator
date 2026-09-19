package com.lab.logistica.importacao.api.dto;

import com.lab.logistica.importacao.dominio.EntregaHistorico;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EntregaHistoricoDto(
        Long id,
        LocalDate dataEntrega,
        String caminhao,
        double cargaToneladas,
        double distanciaKm,
        Double consumoKmL,
        BigDecimal custoDiesel,
        BigDecimal custoOperacional,
        BigDecimal pedagios,
        BigDecimal freteCobrado,
        String origem,
        String destino,
        String arquivoOrigem,
        int linhaPlanilha
) {
    public static EntregaHistoricoDto deDominio(EntregaHistorico e) {
        return new EntregaHistoricoDto(e.getId(), e.getDataEntrega(), e.getCaminhao(), e.getCargaToneladas(),
                e.getDistanciaKm(), e.getConsumoKmL(), e.getCustoDiesel(), e.getCustoOperacional(),
                e.getPedagios(), e.getFreteCobrado(), e.getOrigem(), e.getDestino(),
                e.getArquivoOrigem(), e.getLinhaPlanilha());
    }
}
