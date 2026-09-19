package com.lab.logistica.importacao.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetCsvExporterTest {

    @Test
    void geraCabecalhoMesmoSemLinhas() {
        String csv = DatasetCsvExporter.exportar(List.of());
        assertEquals("id,data_entrega,caminhao,carga_toneladas,distancia_km,consumo_kml,"
                + "custo_diesel,custo_operacional,pedagios,frete_cobrado,origem,destino,"
                + "arquivo_origem,linha_planilha\n", csv);
    }

    @Test
    void escapaCampoComVirgula() {
        EntregaHistorico e = new EntregaHistorico(LocalDate.of(2026, 3, 15), "RODOTREM", 48.0, 500.0, 2.1,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, "São Paulo, SP", "Belém", "x.xlsx", 2, Instant.now());

        String csv = DatasetCsvExporter.exportar(List.of(e));

        assertTrue(csv.contains("\"São Paulo, SP\""));
    }

    @Test
    void camposNulosViramVazio() {
        EntregaHistorico e = new EntregaHistorico(LocalDate.of(2026, 3, 15), "RODOTREM", 48.0, 500.0, null,
                null, null, null, null, null, null, "x.xlsx", 2, Instant.now());

        String csv = DatasetCsvExporter.exportar(List.of(e));
        String linhaDados = csv.split("\n")[1];

        assertEquals(",2026-03-15,RODOTREM,48.0,500.0,,,,,,,,x.xlsx,2", linhaDados);
    }
}
