package com.lab.logistica.importacao.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CelulaLeitorTest {

    private Workbook workbook;
    private Sheet sheet;
    private Row linha;

    @BeforeEach
    void setUp() {
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet();
        linha = sheet.createRow(0);
    }

    @AfterEach
    void tearDown() throws IOException {
        workbook.close();
    }

    private Cell celulaTexto(String valor) {
        Cell c = linha.createCell(linha.getLastCellNum() < 0 ? 0 : linha.getLastCellNum());
        c.setCellValue(valor);
        return c;
    }

    private Cell celulaNumero(double valor) {
        Cell c = linha.createCell(linha.getLastCellNum() < 0 ? 0 : linha.getLastCellNum());
        c.setCellValue(valor);
        return c;
    }

    @Test
    void normalizaCabecalhoComAcentoEspacoMaiuscula() {
        assertEquals("carga_toneladas", CelulaLeitor.normalizarCabecalho("Carga Toneladas"));
        assertEquals("distancia_km", CelulaLeitor.normalizarCabecalho(" Distância (KM) "));
    }

    @Test
    void numeroCelulaNumericaDireta() {
        Cell c = celulaNumero(38.5);
        assertEquals(38.5, CelulaLeitor.numeroOuNulo(c, "campo"));
    }

    @Test
    void numeroTextoComVirgulaDecimalPtBr() {
        Cell c = celulaTexto("1.234,56");
        assertEquals(1234.56, CelulaLeitor.numeroOuNulo(c, "campo"));
    }

    @Test
    void numeroTextoComPontoDecimalEnUs() {
        Cell c = celulaTexto("1234.56");
        assertEquals(1234.56, CelulaLeitor.numeroOuNulo(c, "campo"));
    }

    @Test
    void numeroTextoInvalidoLancaComMensagemDoCampo() {
        Cell c = celulaTexto("abc");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CelulaLeitor.numeroOuNulo(c, "carga_toneladas"));
        assertEquals("carga_toneladas inválido: \"abc\"", ex.getMessage());
    }

    @Test
    void celulaVaziaOuNulaDevolveNuloSemErro() {
        assertNull(CelulaLeitor.numeroOuNulo(null, "campo"));
        Cell vazia = linha.createCell(0, CellType.BLANK);
        assertNull(CelulaLeitor.numeroOuNulo(vazia, "campo"));
    }

    @Test
    void dataTextoIso() {
        Cell c = celulaTexto("2026-03-15");
        assertEquals(LocalDate.of(2026, 3, 15), CelulaLeitor.dataOuNulo(c, "data"));
    }

    @Test
    void dataTextoBr() {
        Cell c = celulaTexto("15/03/2026");
        assertEquals(LocalDate.of(2026, 3, 15), CelulaLeitor.dataOuNulo(c, "data"));
    }

    @Test
    void dataFormatadaComoDataNoExcel() {
        Cell c = linha.createCell(0);
        org.apache.poi.ss.usermodel.CellStyle estilo = workbook.createCellStyle();
        estilo.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("m/d/yy"));
        c.setCellStyle(estilo);
        c.setCellValue(LocalDate.of(2026, 6, 1));
        assertEquals(LocalDate.of(2026, 6, 1), CelulaLeitor.dataOuNulo(c, "data"));
    }

    @Test
    void dataInvalidaLancaComDica() {
        Cell c = celulaTexto("31 de março");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CelulaLeitor.dataOuNulo(c, "data"));
        assertEquals("data inválida: \"31 de março\" (use AAAA-MM-DD ou DD/MM/AAAA)", ex.getMessage());
    }
}
