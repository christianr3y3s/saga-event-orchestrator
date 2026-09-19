package com.lab.logistica.importacao.api.dto;

import com.lab.logistica.importacao.dominio.ConsumoMedio;
import com.lab.logistica.importacao.dominio.FonteConsumo;

/**
 * Forma de transporte HTTP de {@link ConsumoMedio}. {@code fonte} continua String no JSON
 * (contrato público já documentado no README: {@code "historico"} / {@code "tabela_nominal"})
 * mesmo o domínio agora usando o enum {@link FonteConsumo} internamente -- é este mapeamento
 * que faz a ponte entre os dois sem vazar o tipo de domínio para fora nem quebrar o contrato
 * já publicado.
 */
public record ConsumoMedioResponse(
        String caminhao,
        double cargaToneladas,
        double consumoKmL,
        int amostras,
        String fonte
) {
    public static ConsumoMedioResponse deDominio(ConsumoMedio d) {
        String fonte = d.fonte() == FonteConsumo.HISTORICO ? "historico" : "tabela_nominal";
        return new ConsumoMedioResponse(d.caminhao(), d.cargaToneladas(), d.consumoKmL(), d.amostras(), fonte);
    }
}
