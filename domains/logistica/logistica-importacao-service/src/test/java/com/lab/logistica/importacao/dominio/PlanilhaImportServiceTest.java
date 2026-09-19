package com.lab.logistica.importacao.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.logistica.importacao.api.dto.ImportacaoResumo;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlanilhaImportServiceTest {

    private static final List<String> CABECALHO = List.of(
            "Data", "Caminhão", "Carga (Toneladas)", "Distância (KM)", "Consumo KML",
            "Custo Diesel", "Custo Operacional", "Pedagios", "Frete Cobrado", "Origem", "Destino");

    private final EntregaHistoricoRepository repository = mock(EntregaHistoricoRepository.class);
    private final PlanilhaImportService service = new PlanilhaImportService(repository);

    @Test
    void importaLinhasValidasEIgnoraLinhaEmBranco() throws IOException {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        InputStream planilha = PlanilhaTestHelper.planilha(CABECALHO, List.of(
                List.of("2026-03-15", "carreta_4_eixos", "38", "500", "2,6", "6,00", "2,50", "120,00", "2877,39", "SP", "RJ"),
                List.of("", "", "", "", "", "", "", "", "", "", ""), // linha em branco -- deve ser ignorada
                List.of("2026-03-16", "RODOTREM", "48", "1000", "", "6,00", "3,00", "350,00", "", "PA", "SP")
        ));

        ImportacaoResumo resumo = service.importar("entregas.xlsx", planilha);

        assertEquals(2, resumo.totalLinhas());
        assertEquals(2, resumo.importadas());
        assertEquals(0, resumo.rejeitadas());
        assertTrue(resumo.erros().isEmpty());

        ArgumentCaptor<List<EntregaHistorico>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<EntregaHistorico> salvas = captor.getValue();
        assertEquals(2, salvas.size());
        assertEquals("CARRETA_4_EIXOS", salvas.get(0).getCaminhao());
        assertEquals(38.0, salvas.get(0).getCargaToneladas());
        assertEquals(2.6, salvas.get(0).getConsumoKmL());
        assertEquals("entregas.xlsx", salvas.get(0).getArquivoOrigem());
        assertEquals(2, salvas.get(0).getLinhaPlanilha()); // linha 1 = cabeçalho, linha 2 = primeira linha de dados
    }

    @Test
    void geraAlertaQuandoConsumoMedidoAbaixoDoLimite() throws IOException {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        InputStream planilha = PlanilhaTestHelper.planilha(CABECALHO, List.of(
                List.of("2026-03-15", "RODOTREM", "48", "500", "1,8", "", "", "", "", "", "")
        ));

        ImportacaoResumo resumo = service.importar("entregas.xlsx", planilha);

        assertEquals(1, resumo.alertasConsumo().size());
        assertEquals(1.8, resumo.alertasConsumo().get(0).consumoKmL());
        assertEquals(2, resumo.alertasConsumo().get(0).linha());
    }

    @Test
    void rejeitaLinhaComCaminhaoDesconhecidoMasImportaAsOutras() throws IOException {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        InputStream planilha = PlanilhaTestHelper.planilha(CABECALHO, List.of(
                List.of("2026-03-15", "BITREM", "38", "500", "", "", "", "", "", "", ""),
                List.of("2026-03-16", "RODOTREM", "48", "1000", "", "", "", "", "", "", "")
        ));

        ImportacaoResumo resumo = service.importar("entregas.xlsx", planilha);

        assertEquals(2, resumo.totalLinhas());
        assertEquals(1, resumo.importadas());
        assertEquals(1, resumo.rejeitadas());
        assertTrue(resumo.erros().get(0).motivo().contains("BITREM"));
        assertEquals(2, resumo.erros().get(0).linha());
    }

    @Test
    void rejeitaLinhaSemCamposObrigatorios() throws IOException {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        InputStream planilha = PlanilhaTestHelper.planilha(CABECALHO, List.of(
                List.of("2026-03-15", "RODOTREM", "", "500", "", "", "", "", "", "", "")
        ));

        ImportacaoResumo resumo = service.importar("entregas.xlsx", planilha);

        assertEquals(1, resumo.rejeitadas());
        assertTrue(resumo.erros().get(0).motivo().contains("carga_toneladas"));
    }

    @Test
    void rejeitaPlanilhaSemColunasObrigatorias() throws IOException {
        InputStream planilha = PlanilhaTestHelper.planilha(List.of("data", "origem"), List.of(
                List.of("2026-03-15", "SP")
        ));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importar("entregas.xlsx", planilha));
        assertTrue(ex.getMessage().contains("caminhao"));
        assertTrue(ex.getMessage().contains("carga_toneladas"));
        assertTrue(ex.getMessage().contains("distancia_km"));
    }
}
