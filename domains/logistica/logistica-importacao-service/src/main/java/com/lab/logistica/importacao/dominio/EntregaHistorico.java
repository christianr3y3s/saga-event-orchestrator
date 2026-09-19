package com.lab.logistica.importacao.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Uma linha de entrega histórica importada de planilha: o que foi transportado, com que
 * caminhão, a que custo e (quando informado) com que consumo real medido. Alimenta dois
 * consumidores (ver README): calibração de consumo em logistica-rota-service e dataset
 * para treinamento de IA.
 *
 * <p>Mantém tudo que a planilha trouxe, mesmo campos opcionais vazios -- é histórico bruto,
 * não um agregado; a limpeza/feature engineering para IA acontece rio abaixo, não aqui.
 */
@Entity
@Table(name = "entrega_historico")
public class EntregaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate dataEntrega;

    /**
     * Nome do modelo de caminhão (RODOTREM, CARRETA_4_EIXOS, CARRETA_30T -- mesmos nomes
     * de TipoCaminhao em logistica-rota-service). Guardado como texto, não como referência
     * de chave estrangeira a outro serviço -- os dois módulos são independentes por
     * convenção deste repositório (ver DESIGN.md / README de logistica).
     */
    @Column(nullable = false)
    private String caminhao;

    @Column(nullable = false)
    private double cargaToneladas;

    @Column(nullable = false)
    private double distanciaKm;

    /** Consumo REAL medido nesta entrega, se informado. Nulo quando a planilha não trouxe. */
    private Double consumoKmL;

    private BigDecimal custoDiesel;
    private BigDecimal custoOperacional;
    private BigDecimal pedagios;

    /** Frete efetivamente cobrado/pago nesta entrega, se a planilha trouxer. */
    private BigDecimal freteCobrado;

    private String origem;
    private String destino;

    @Column(nullable = false)
    private String arquivoOrigem;

    @Column(nullable = false)
    private int linhaPlanilha;

    @Column(nullable = false)
    private Instant importadoEm;

    protected EntregaHistorico() {
        // exigido pelo JPA
    }

    public EntregaHistorico(LocalDate dataEntrega, String caminhao, double cargaToneladas, double distanciaKm,
                             Double consumoKmL, BigDecimal custoDiesel, BigDecimal custoOperacional,
                             BigDecimal pedagios, BigDecimal freteCobrado, String origem, String destino,
                             String arquivoOrigem, int linhaPlanilha, Instant importadoEm) {
        this.dataEntrega = dataEntrega;
        this.caminhao = caminhao;
        this.cargaToneladas = cargaToneladas;
        this.distanciaKm = distanciaKm;
        this.consumoKmL = consumoKmL;
        this.custoDiesel = custoDiesel;
        this.custoOperacional = custoOperacional;
        this.pedagios = pedagios;
        this.freteCobrado = freteCobrado;
        this.origem = origem;
        this.destino = destino;
        this.arquivoOrigem = arquivoOrigem;
        this.linhaPlanilha = linhaPlanilha;
        this.importadoEm = importadoEm;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDataEntrega() {
        return dataEntrega;
    }

    public String getCaminhao() {
        return caminhao;
    }

    public double getCargaToneladas() {
        return cargaToneladas;
    }

    public double getDistanciaKm() {
        return distanciaKm;
    }

    public Double getConsumoKmL() {
        return consumoKmL;
    }

    public BigDecimal getCustoDiesel() {
        return custoDiesel;
    }

    public BigDecimal getCustoOperacional() {
        return custoOperacional;
    }

    public BigDecimal getPedagios() {
        return pedagios;
    }

    public BigDecimal getFreteCobrado() {
        return freteCobrado;
    }

    public String getOrigem() {
        return origem;
    }

    public String getDestino() {
        return destino;
    }

    public String getArquivoOrigem() {
        return arquivoOrigem;
    }

    public int getLinhaPlanilha() {
        return linhaPlanilha;
    }

    public Instant getImportadoEm() {
        return importadoEm;
    }
}
