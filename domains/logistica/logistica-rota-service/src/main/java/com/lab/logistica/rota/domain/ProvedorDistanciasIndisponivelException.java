package com.lab.logistica.rota.domain;

/**
 * O provedor externo de distância (ex.: OpenRouteService) falhou ou respondeu algo
 * inutilizável -- erro do lado de lá, não da requisição do cliente. A API traduz isto em 502
 * (em vez de 500 genérico ou 400), para o chamador saber que tentar de novo pode funcionar.
 */
public class ProvedorDistanciasIndisponivelException extends IllegalStateException {

    public ProvedorDistanciasIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ProvedorDistanciasIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
