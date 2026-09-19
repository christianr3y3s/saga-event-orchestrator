package com.lab.nfe.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AutorizadorResolverTest {

    private final AutorizadorResolver resolver = new AutorizadorResolver();

    @Test
    void paUsaSvrs() {
        assertEquals(Autorizador.SVRS, resolver.autorizadorNormal(Uf.PA));
    }

    @ParameterizedTest
    @EnumSource(value = Uf.class, names = "PA", mode = EnumSource.Mode.EXCLUDE)
    void qualquerOutraUfUsaSefazSpNestaRegraProvisoria(Uf uf) {
        // Documenta a regra pedida -- ver o alerta de correção em AutorizadorResolver:
        // isto está tecnicamente errado para qualquer UF que não seja SP, e só "funciona"
        // hoje porque o catálogo de clientes é PA + SP.
        assertEquals(Autorizador.SEFAZ_SP, resolver.autorizadorNormal(uf));
    }

    @Test
    void contingenciaEhSempreSvcAnIndependenteDaUf() {
        for (Uf uf : Uf.values()) {
            assertEquals(Autorizador.SVC_AN, resolver.autorizadorContingencia(uf));
        }
    }

    @Test
    void resolverEndpointNormalParaPaApontaParaSvrs() {
        URI uri = resolver.resolverEndpoint(Uf.PA, 1, SefazAmbiente.PRODUCAO, SefazServico.AUTORIZACAO);
        assertEquals("https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx", uri.toString());
    }

    @Test
    void resolverEndpointNormalParaSpApontaParaSefazSp() {
        URI uri = resolver.resolverEndpoint(Uf.SP, 1, SefazAmbiente.PRODUCAO, SefazServico.AUTORIZACAO);
        assertEquals("https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx", uri.toString());
    }

    @Test
    void resolverEndpointContingenciaApontaParaSvcAn() {
        URI uri = resolver.resolverEndpoint(Uf.PA, 6, SefazAmbiente.PRODUCAO, SefazServico.AUTORIZACAO);
        assertTrue(uri.getHost().contains("svc.fazenda.gov.br"));
    }

    @Test
    void tpEmisNaoSuportadoLancaExcecao() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolverEndpoint(Uf.PA, 2, SefazAmbiente.PRODUCAO, SefazServico.AUTORIZACAO));
    }

    @Test
    void servicoIndisponivelNoAutorizadorLancaExcecaoComContexto() {
        // SVC_AN nao tem INUTILIZACAO -- resolverEndpoint deve explicar, nao vazar Optional vazio.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolverEndpoint(Uf.PA, 6, SefazAmbiente.PRODUCAO, SefazServico.INUTILIZACAO));
        assertTrue(ex.getMessage().contains("INUTILIZACAO"));
    }

    @Test
    void tabelaCustomizadaSubstituiOPadrao() {
        // Prova de que a estrutura permite plugar a tabela real UF->autorizador depois,
        // sem mudar a API do resolver.
        AutorizadorResolver comMg = new AutorizadorResolver(Map.of(Uf.PA, Autorizador.SVRS, Uf.MG, Autorizador.SVRS),
                Autorizador.SEFAZ_SP);
        assertEquals(Autorizador.SVRS, comMg.autorizadorNormal(Uf.MG));
        assertEquals(Autorizador.SEFAZ_SP, comMg.autorizadorNormal(Uf.BA));
    }
}
