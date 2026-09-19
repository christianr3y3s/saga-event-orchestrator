package com.lab.nfe.sefaz;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

/**
 * Regra de roteamento pedida para esta fase do projeto: UF = PA usa o autorizador SVRS;
 * qualquer outra UF usa SEFAZ-SP. Implementada literalmente como pedido, mas com uma
 * ressalva técnica importante que vale registrar (ver {@link #TABELA_DEFAULT}).
 *
 * <p><b>Isto não é a regra real da NF-e.</b> Cada UF tem seu próprio autorizador
 * contratado -- várias usam a SVRS (hoje: AC, AL, AP, BA, CE, DF, ES, GO, MA, MS, MT, PA,
 * PB, PE, PI, RJ, RN, RO, RR, SC, SE, TO -- confirme a lista atual no Portal Nacional, ela
 * muda de tempos em tempos), e a SEFAZ-SP só autoriza NF-e de emitentes cadastrados em São
 * Paulo -- ela rejeita (ou nem responde de forma utilizável) uma NF-e de um emitente de
 * outra UF. Ou seja: usar SEFAZ-SP como "padrão" para qualquer UF que não seja PA vai
 * falhar na primeira nota emitida por um cliente de, por exemplo, MG ou RS.
 *
 * <p>Isto está implementado assim porque foi pedido explicitamente e porque hoje o
 * catálogo de UFs atendidas é PA e SP -- funciona exatamente para essas duas. Antes de
 * cadastrar um emitente de qualquer outra UF, troque {@link #TABELA_DEFAULT} por uma
 * tabela completa de UF -&gt; autorizador (a estrutura já é um Map, então é só preencher
 * as outras 25 entradas).
 */
public final class AutorizadorResolver {

    /** UF -> autorizador quando não há contingência (tpEmis normal). */
    private final Map<Uf, Autorizador> tabela;

    /** Autorizador usado para qualquer UF sem entrada explícita na tabela. */
    private final Autorizador autorizadorPadrao;

    public static final Map<Uf, Autorizador> TABELA_DEFAULT = Map.of(Uf.PA, Autorizador.SVRS);
    public static final Autorizador PADRAO_DEFAULT = Autorizador.SEFAZ_SP;

    public AutorizadorResolver() {
        this(TABELA_DEFAULT, PADRAO_DEFAULT);
    }

    public AutorizadorResolver(Map<Uf, Autorizador> tabela, Autorizador autorizadorPadrao) {
        this.tabela = new EnumMap<>(tabela);
        this.autorizadorPadrao = autorizadorPadrao;
    }

    /** Autorizador em operação normal (tpEmis = 1) para a UF do emitente. */
    public Autorizador autorizadorNormal(Uf ufEmitente) {
        return tabela.getOrDefault(ufEmitente, autorizadorPadrao);
    }

    /** Autorizador de contingência (tpEmis = 6, SVC-AN) -- igual para todas as UFs. */
    public Autorizador autorizadorContingencia(Uf ufEmitente) {
        return Autorizador.SVC_AN;
    }

    /**
     * Endpoint SOAP completo para a UF, ambiente, serviço e tipo de emissão informados.
     *
     * @param tpEmis 1 = emissão normal, 6 = contingência SVC-AN
     */
    public URI resolverEndpoint(Uf ufEmitente, int tpEmis, SefazAmbiente ambiente, SefazServico servico) {
        Autorizador autorizador = switch (tpEmis) {
            case 1 -> autorizadorNormal(ufEmitente);
            case 6 -> autorizadorContingencia(ufEmitente);
            default -> throw new IllegalArgumentException("tpEmis não suportado: " + tpEmis);
        };
        return autorizador.uri(ambiente, servico)
                .orElseThrow(() -> new IllegalStateException(
                        "Serviço " + servico + " indisponível em " + autorizador + " (UF=" + ufEmitente
                                + ", tpEmis=" + tpEmis + ")"));
    }
}
