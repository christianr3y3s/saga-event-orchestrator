package com.lab.logistica.importacao.dominio;

import java.util.List;

/**
 * CSV simples do histórico completo, para pipelines externos de treinamento de IA
 * (task 3). Sem dependência extra: escrita manual com escaping RFC 4180 (aspas duplicadas,
 * campo entre aspas quando tem vírgula/aspas/quebra de linha).
 */
public final class DatasetCsvExporter {

    private static final String[] CABECALHO = {
            "id", "data_entrega", "caminhao", "carga_toneladas", "distancia_km", "consumo_kml",
            "custo_diesel", "custo_operacional", "pedagios", "frete_cobrado", "origem", "destino",
            "arquivo_origem", "linha_planilha"
    };

    private DatasetCsvExporter() {
    }

    public static String exportar(List<EntregaHistorico> entregas) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CABECALHO)).append('\n');
        for (EntregaHistorico e : entregas) {
            sb.append(linha(e)).append('\n');
        }
        return sb.toString();
    }

    private static String linha(EntregaHistorico e) {
        return String.join(",",
                campo(e.getId()),
                campo(e.getDataEntrega()),
                campo(e.getCaminhao()),
                campo(e.getCargaToneladas()),
                campo(e.getDistanciaKm()),
                campo(e.getConsumoKmL()),
                campo(e.getCustoDiesel()),
                campo(e.getCustoOperacional()),
                campo(e.getPedagios()),
                campo(e.getFreteCobrado()),
                campo(e.getOrigem()),
                campo(e.getDestino()),
                campo(e.getArquivoOrigem()),
                campo(e.getLinhaPlanilha()));
    }

    private static String campo(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = String.valueOf(valor);
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
