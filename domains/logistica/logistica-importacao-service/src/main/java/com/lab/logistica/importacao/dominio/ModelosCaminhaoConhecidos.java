package com.lab.logistica.importacao.dominio;

import java.util.Map;
import java.util.Set;

/**
 * Cópia local, só com o que este serviço precisa (nomes válidos + consumo nominal de
 * fallback), da mesma tabela validada em TipoCaminhao (logistica-rota-service). Os dois
 * módulos não compartilham código de propósito -- é a mesma convenção de isolamento entre
 * domínios já usada em cashback/loja (ver DESIGN.md). Se a tabela mudar, atualize as DUAS
 * cópias: aqui e em logistica-rota-service.domain.TipoCaminhao.
 */
public final class ModelosCaminhaoConhecidos {

    public record Nominal(double capacidadeToneladas, double kmLVazio, double kmLCarregado) {
        double consumoKmL(double cargaToneladas) {
            double fracao = Math.max(0, Math.min(1, cargaToneladas / capacidadeToneladas));
            return kmLVazio + (kmLCarregado - kmLVazio) * fracao;
        }
    }

    private static final Map<String, Nominal> TABELA = Map.of(
            "RODOTREM", new Nominal(48.0, 2.85, 2.15),
            "CARRETA_4_EIXOS", new Nominal(38.0, 3.15, 2.65),
            "CARRETA_30T", new Nominal(30.0, 3.15, 2.85)
    );

    public static final Set<String> NOMES_VALIDOS = TABELA.keySet();

    private ModelosCaminhaoConhecidos() {
    }

    public static boolean ehValido(String nome) {
        return nome != null && TABELA.containsKey(nome.trim().toUpperCase());
    }

    /** Consumo nominal (tabela) para o modelo e carga informados -- usado como fallback sem histórico. */
    public static double consumoNominalKmL(String caminhao, double cargaToneladas) {
        Nominal n = TABELA.get(caminhao.trim().toUpperCase());
        if (n == null) {
            throw new IllegalArgumentException("Modelo de caminhão desconhecido: " + caminhao);
        }
        return n.consumoKmL(cargaToneladas);
    }
}
