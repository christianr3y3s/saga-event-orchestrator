package com.lab.logistica.importacao.dominio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Monta um .xlsx em memória a partir de linhas de texto, para testar o parser sem arquivo em disco. */
final class PlanilhaTestHelper {

    private PlanilhaTestHelper() {
    }

    static InputStream planilha(List<String> cabecalho, List<List<String>> linhas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            escreverLinha(sheet, 0, cabecalho);
            for (int i = 0; i < linhas.size(); i++) {
                escreverLinha(sheet, i + 1, linhas.get(i));
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            workbook.write(bytes);
            return new ByteArrayInputStream(bytes.toByteArray());
        }
    }

    private static void escreverLinha(Sheet sheet, int indice, List<String> valores) {
        Row row = sheet.createRow(indice);
        for (int c = 0; c < valores.size(); c++) {
            String valor = valores.get(c);
            if (valor != null) {
                row.createCell(c).setCellValue(valor);
            }
        }
    }
}
