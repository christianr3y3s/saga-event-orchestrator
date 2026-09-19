package com.lab.logistica.importacao.dominio;

import java.util.List;

/**
 * Resultado de {@link PlanilhaImportService#importar(String, java.io.InputStream)}. Tipo do
 * domínio -- a forma de transporte HTTP é {@code api.dto.ImportacaoResumo}, que mapeia este
 * record via {@code deDominio(...)}. Antes desta separação, o próprio serviço de domínio
 * devolvia o DTO da API diretamente (violação da regra de dependência: domínio não deveria
 * conhecer a camada de transporte).
 */
public record ResumoImportacao(
        String arquivo,
        int totalLinhas,
        int importadas,
        int rejeitadas,
        List<LinhaRejeitadaImportacao> erros,
        List<AlertaConsumoMedido> alertasConsumo
) {
}
