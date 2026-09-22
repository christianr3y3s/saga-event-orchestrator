package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Caso de negócio pedido para o case de apresentação ao mercado: Rodotrem saindo de Belém
 * (PA) para o Rio de Janeiro (RJ) e voltando cheio de asfalto -- com uma incerteza real (50%
 * de chance de conseguir carga na ida em vez de rodar vazio) modelada como dois limites
 * (ida 100% vazia / ida 100% carregada) em vez de uma fração de trajeto inventada, porque
 * não há dado real de peso/trecho para essa carga hipotética.
 *
 * <p>Usa exatamente {@link CalculadoraFrete}/{@link ParametrosFrete}/{@link TipoCaminhao} de
 * produção -- nenhuma fórmula é reescrita aqui, só os parâmetros do cenário. Isto é o mesmo
 * papel que {@code CalculadoraFreteTest} já cumpre para os Casos A/B/C: fixar um número que,
 * se uma fórmula mudar, tem que ser atualizado deliberadamente, não silenciosamente.
 *
 * <p><b>Premissas que NÃO vieram do pedido do negócio (documentadas para poder trocar):</b>
 * <ul>
 *   <li>Distância Belém-RJ: 3.125 km. Fontes públicas convergem em 3.080-3.200 km rodoviários
 *       (não é linha reta) -- ainda não confirmado contra o
 *       {@code logistica-rota-service} com {@code distancia-provider=openrouteservice} (ver
 *       README, seção "Em aberto"). Troque por esse valor antes de qualquer apresentação.</li>
 *   <li>Diesel R$ 6,20/L, custo operacional R$ 3,00/km, pedágio R$ 375,00 por trecho
 *       (estimativa grosseira ~R$0,12/km) -- nenhum vem de uma tabela de preço real.</li>
 *   <li>O cenário "carregado" na ida modela só o CUSTO extra de rodar com peso (mais
 *       diesel) -- não simula receita de frete para essa carga hipotética, porque não há
 *       peso/preço real informado. Ver {@link #custoExtraDeRodarCarregadoNaIda()}.</li>
 * </ul>
 */
class CenarioBelemRioTest {

    private static final TipoCaminhao CAMINHAO = TipoCaminhao.RODOTREM;
    private static final double DISTANCIA_KM = 3125.0;
    private static final BigDecimal PRECO_DIESEL = new BigDecimal("6.20");
    private static final BigDecimal CUSTO_OPERACIONAL_KM = new BigDecimal("3.00");
    private static final BigDecimal PEDAGIOS_POR_TRECHO = new BigDecimal("375.00");

    private final CalculadoraFrete calculadora = new CalculadoraFrete();

    private ResultadoFrete calcularTrecho(double cargaToneladas) {
        return calculadora.calcular(new ParametrosFrete(
                CAMINHAO, cargaToneladas, DISTANCIA_KM, PRECO_DIESEL, PEDAGIOS_POR_TRECHO,
                CUSTO_OPERACIONAL_KM, BigDecimal.ZERO)); // sem margem -- isto é custo, não preço de venda
    }

    @Test
    void idaVazia() {
        ResultadoFrete r = calcularTrecho(0.0);
        assertEquals(2.85, r.consumoKmL(), 1e-9);
        assertEquals(new BigDecimal("6798.25"), r.combustivel());
        assertEquals(new BigDecimal("9375.00"), r.operacional());
        assertEquals(new BigDecimal("16548.25"), r.custoTotal());
    }

    @Test
    void idaCarregadaNoLimiteSuperior() {
        // "Arrumou carga logo na saída de Belém" -- o limite superior de custo de diesel
        // para a chance de 50% de não rodar vazio, sem inventar peso/trecho parcial.
        ResultadoFrete r = calcularTrecho(CAMINHAO.capacidadeToneladas());
        assertEquals(2.15, r.consumoKmL(), 1e-9);
        assertEquals(new BigDecimal("9011.63"), r.combustivel());
        assertEquals(new BigDecimal("18761.63"), r.custoTotal());
    }

    @Test
    void voltaSempreCheiaDeAsfalto() {
        // Mesma carga (capacidade máxima) e mesma distância da ida carregada -- por isso o
        // custo bate com idaCarregadaNoLimiteSuperior(); é intencional, não coincidência.
        ResultadoFrete r = calcularTrecho(CAMINHAO.capacidadeToneladas());
        assertEquals(new BigDecimal("18761.63"), r.custoTotal());
    }

    @Test
    void custoTotalDaIdaEVoltaNosDoisLimites() {
        BigDecimal custoIdaEVoltaSeIdaVazia = calcularTrecho(0.0).custoTotal()
                .add(calcularTrecho(CAMINHAO.capacidadeToneladas()).custoTotal());
        BigDecimal custoIdaEVoltaSeIdaCarregada = calcularTrecho(CAMINHAO.capacidadeToneladas()).custoTotal()
                .add(calcularTrecho(CAMINHAO.capacidadeToneladas()).custoTotal());

        assertEquals(new BigDecimal("35309.88"), custoIdaEVoltaSeIdaVazia);
        assertEquals(new BigDecimal("37523.26"), custoIdaEVoltaSeIdaCarregada);

        // Custo esperado com 50% de chance para cada cenário da ida.
        BigDecimal custoEsperado = custoIdaEVoltaSeIdaVazia.add(custoIdaEVoltaSeIdaCarregada)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("36416.57"), custoEsperado);
    }

    @Test
    void custoExtraDeRodarCarregadoNaIda() {
        // O quanto a MAIS em diesel/operacional custa rodar a ida carregada em vez de
        // vazia -- isto é custo, não receita. Para a carga do caminho valer a pena, o frete
        // cobrado por ela precisa cobrir pelo menos este valor (calcule com
        // POST /fretes/calcular usando a distância real do trecho onde a carga andou).
        BigDecimal extra = calcularTrecho(CAMINHAO.capacidadeToneladas()).custoTotal()
                .subtract(calcularTrecho(0.0).custoTotal());
        assertEquals(new BigDecimal("2213.38"), extra);
    }
}
