package com.lab.logistica.importacao.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricoConsumoServiceTest {

    private final EntregaHistoricoRepository repository = mock(EntregaHistoricoRepository.class);
    private final HistoricoConsumoService service = new HistoricoConsumoService(repository);

    private EntregaHistorico entrega(double carga, double consumoKmL) {
        return new EntregaHistorico(LocalDate.now(), "RODOTREM", carga, 500.0, consumoKmL,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, null, "SP", "RJ", "x.xlsx", 2, Instant.now());
    }

    @Test
    void semHistoricoUsaTabelaNominal() {
        when(repository.findByCaminhaoAndConsumoKmLIsNotNull("RODOTREM")).thenReturn(List.of());

        ConsumoMedio r = service.consumoMedio("rodotrem", 48.0);

        assertEquals(FonteConsumo.TABELA_NOMINAL, r.fonte());
        assertEquals(0, r.amostras());
        assertEquals(2.15, r.consumoKmL(), 1e-9);
    }

    @Test
    void comHistoricoUsaMediaDoBucketCorrespondente() {
        when(repository.findByCaminhaoAndConsumoKmLIsNotNull("RODOTREM")).thenReturn(List.of(
                entrega(48.0, 2.0), // carregado
                entrega(45.0, 2.2), // carregado
                entrega(0.0, 2.9)   // vazio -- não deve entrar na média de "carregado"
        ));

        ConsumoMedio r = service.consumoMedio("RODOTREM", 40.0); // carga > 0 -> bucket "carregado"

        assertEquals(FonteConsumo.HISTORICO, r.fonte());
        assertEquals(2, r.amostras());
        assertEquals(2.1, r.consumoKmL(), 1e-9);
    }

    @Test
    void bucketVazioSoUsaAmostrasVazias() {
        when(repository.findByCaminhaoAndConsumoKmLIsNotNull("RODOTREM")).thenReturn(List.of(
                entrega(48.0, 2.0),
                entrega(0.0, 2.9)
        ));

        ConsumoMedio r = service.consumoMedio("RODOTREM", 0.0);

        assertEquals(1, r.amostras());
        assertEquals(2.9, r.consumoKmL(), 1e-9);
    }

    @Test
    void rejeitaModeloDesconhecido() {
        assertThrows(IllegalArgumentException.class, () -> service.consumoMedio("BITREM", 10.0));
    }
}
