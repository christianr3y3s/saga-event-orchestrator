package com.lab.logistica.rota.api;

/** Corpo de erro simples e uniforme -- sem framework de validação, no mesmo espírito enxuto do resto do repositório. */
public record ErroResposta(String mensagem) {
}
