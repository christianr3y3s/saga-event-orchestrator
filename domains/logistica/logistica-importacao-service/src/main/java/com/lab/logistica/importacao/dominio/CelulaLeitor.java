package com.lab.logistica.importacao.dominio;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * Leitura tolerante de células de planilha: aceita número já numérico OU texto (com
 * vírgula OU ponto decimal, já que a planilha pode vir de Excel PT-BR ou EN-US), e data
 * já formatada como data OU texto em alguns formatos comuns. Lança IllegalArgumentException
 * com mensagem curta em vez de deixar vazar NumberFormatException/ParseException cru --
 * quem chama decide o que fazer (aqui, rejeitar só a linha).
 */
final class CelulaLeitor {

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,          // 2026-03-15
            DateTimeFormatter.ofPattern("dd/MM/yyyy"), // 15/03/2026
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private CelulaLeitor() {
    }

    static String normalizarCabecalho(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[\\s()]+", "_").replaceAll("_+$", "");
    }

    static String textoOuNulo(Cell celula) {
        if (celula == null || celula.getCellType() == CellType.BLANK) {
            return null;
        }
        String valor = switch (celula.getCellType()) {
            case STRING -> celula.getStringCellValue();
            case NUMERIC -> String.valueOf(celula.getNumericCellValue());
            case BOOLEAN -> String.valueOf(celula.getBooleanCellValue());
            default -> null;
        };
        if (valor == null) {
            return null;
        }
        valor = valor.trim();
        return valor.isEmpty() ? null : valor;
    }

    /** Número (aceita "1234,56" ou "1234.56" quando a célula é texto). Nulo se a célula estiver vazia. */
    static Double numeroOuNulo(Cell celula, String nomeCampo) {
        if (celula == null || celula.getCellType() == CellType.BLANK) {
            return null;
        }
        if (celula.getCellType() == CellType.NUMERIC) {
            return celula.getNumericCellValue();
        }
        String texto = textoOuNulo(celula);
        if (texto == null) {
            return null;
        }
        String normalizado = texto.replace(".", "").replace(",", ".");
        // Se não tinha vírgula, o replace acima pode ter apagado o separador decimal
        // de um número em formato EN-US ("1234.56" -> "123456") -- desfaz nesse caso.
        if (!texto.contains(",") && texto.contains(".")) {
            normalizado = texto;
        }
        try {
            return Double.parseDouble(normalizado);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nomeCampo + " inválido: \"" + texto + "\"");
        }
    }

    static BigDecimal decimalOuNulo(Cell celula, String nomeCampo) {
        Double valor = numeroOuNulo(celula, nomeCampo);
        return valor == null ? null : BigDecimal.valueOf(valor);
    }

    static LocalDate dataOuNulo(Cell celula, String nomeCampo) {
        if (celula == null || celula.getCellType() == CellType.BLANK) {
            return null;
        }
        if (celula.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celula)) {
            return celula.getLocalDateTimeCellValue().toLocalDate();
        }
        String texto = textoOuNulo(celula);
        if (texto == null) {
            return null;
        }
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (Exception ignorada) {
                // tenta o próximo formato
            }
        }
        try {
            return LocalDateTime.parse(texto).toLocalDate();
        } catch (Exception ignorada) {
            throw new IllegalArgumentException(nomeCampo + " inválida: \"" + texto + "\" (use AAAA-MM-DD ou DD/MM/AAAA)");
        }
    }
}
