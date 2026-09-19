package com.lab.nfe.sefaz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class AutorizadorTest {

    @Test
    void svrsProducaoAutorizacao() {
        URI uri = Autorizador.SVRS.uri(SefazAmbiente.PRODUCAO, SefazServico.AUTORIZACAO).orElseThrow();
        assertEquals("https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx", uri.toString());
    }

    @Test
    void sefazSpProducaoAutorizacao() {
        URI uri = Autorizador.SEFAZ_SP.uri(SefazAmbiente.PRODUCAO, SefazServico.AUTORIZACAO).orElseThrow();
        assertEquals("https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx", uri.toString());
    }

    @Test
    void sefazSpHomologacaoUsaOutroHost() {
        URI uri = Autorizador.SEFAZ_SP.uri(SefazAmbiente.HOMOLOGACAO, SefazServico.AUTORIZACAO).orElseThrow();
        assertEquals("homologacao.nfe.fazenda.sp.gov.br", uri.getHost());
    }

    @Test
    void svcAnNaoTemInutilizacao() {
        assertTrue(Autorizador.SVC_AN.uri(SefazAmbiente.PRODUCAO, SefazServico.INUTILIZACAO).isEmpty());
    }

    @Test
    void svrsHomologacaoUsaOutroHost() {
        URI uri = Autorizador.SVRS.uri(SefazAmbiente.HOMOLOGACAO, SefazServico.EVENTO).orElseThrow();
        assertEquals("nfe-homologacao.svrs.rs.gov.br", uri.getHost());
    }
}
