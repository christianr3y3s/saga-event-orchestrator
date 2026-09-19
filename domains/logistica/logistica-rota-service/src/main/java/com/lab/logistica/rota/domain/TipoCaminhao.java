package com.lab.logistica.rota.domain;

/**
 * Tabela de modelos validada em domains/logistica (ver DESIGN.md): capacidade e consumo
 * médio vazio/carregado. Os valores de km/L são o ponto médio de cada faixa informada.
 */
public enum TipoCaminhao {

    RODOTREM("Rodotrem", 48.0, 2.85, 2.15),
    CARRETA_4_EIXOS("Carreta 4 Eixos", 38.0, 3.15, 2.65),
    CARRETA_30T("Carreta 30 Toneladas", 30.0, 3.15, 2.85);

    private final String nomeExibicao;
    private final double capacidadeToneladas;
    private final double kmLVazio;
    private final double kmLCarregado;

    TipoCaminhao(String nomeExibicao, double capacidadeToneladas, double kmLVazio, double kmLCarregado) {
        this.nomeExibicao = nomeExibicao;
        this.capacidadeToneladas = capacidadeToneladas;
        this.kmLVazio = kmLVazio;
        this.kmLCarregado = kmLCarregado;
    }

    public String nomeExibicao() {
        return nomeExibicao;
    }

    public double capacidadeToneladas() {
        return capacidadeToneladas;
    }

    public double kmLVazio() {
        return kmLVazio;
    }

    public double kmLCarregado() {
        return kmLCarregado;
    }

    /** Consumo nominal (km/L) interpolado linearmente entre vazio e carga máxima. */
    public double consumoKmL(double cargaToneladas) {
        if (Double.isNaN(cargaToneladas) || cargaToneladas < 0 || cargaToneladas > capacidadeToneladas) {
            throw new IllegalArgumentException(
                    "Carga de " + cargaToneladas + " t fora da capacidade (0.." + capacidadeToneladas + " t) de " + nomeExibicao);
        }
        double fracao = cargaToneladas / capacidadeToneladas;
        return kmLVazio + (kmLCarregado - kmLVazio) * fracao;
    }
}
