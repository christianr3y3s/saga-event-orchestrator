package com.lab.nfe.sefaz;

import static com.lab.nfe.sefaz.SefazAmbiente.HOMOLOGACAO;
import static com.lab.nfe.sefaz.SefazAmbiente.PRODUCAO;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Autorizadores de NF-e. URLs conferidas de memória, não contra o Portal Nacional da
 * NF-e -- confirme antes de apontar para produção de verdade (ver README.md deste
 * módulo, seção "Confiabilidade dos endpoints").
 */
public enum Autorizador {

    SVRS(
        Map.of(PRODUCAO, "https://nfe.svrs.rs.gov.br",
               HOMOLOGACAO, "https://nfe-homologacao.svrs.rs.gov.br"),
        Map.of(
            SefazServico.STATUS_SERVICO,     "/ws/NfeStatusServico/NfeStatusServico4.asmx",
            SefazServico.AUTORIZACAO,        "/ws/NfeAutorizacao/NFeAutorizacao4.asmx",
            SefazServico.RET_AUTORIZACAO,    "/ws/NfeRetAutorizacao/NFeRetAutorizacao4.asmx",
            SefazServico.CONSULTA_PROTOCOLO, "/ws/NfeConsulta/NfeConsulta4.asmx",
            SefazServico.INUTILIZACAO,       "/ws/nfeinutilizacao/nfeinutilizacao4.asmx",
            SefazServico.EVENTO,             "/ws/recepcaoevento/recepcaoevento4.asmx")),

    /**
     * Autorizador próprio da SEFAZ-SP -- só autoriza NF-e de emitentes cadastrados em
     * São Paulo. Ver AutorizadorResolver para a ressalva importante sobre usar isto como
     * "padrão" para UFs que não são SP.
     */
    SEFAZ_SP(
        Map.of(PRODUCAO, "https://nfe.fazenda.sp.gov.br",
               HOMOLOGACAO, "https://homologacao.nfe.fazenda.sp.gov.br"),
        Map.of(
            SefazServico.STATUS_SERVICO,     "/ws/nfestatusservico4.asmx",
            SefazServico.AUTORIZACAO,        "/ws/nfeautorizacao4.asmx",
            SefazServico.RET_AUTORIZACAO,    "/ws/nferetautorizacao4.asmx",
            SefazServico.CONSULTA_PROTOCOLO, "/ws/nfeconsultaprotocolo4.asmx",
            SefazServico.INUTILIZACAO,       "/ws/nfeinutilizacao4.asmx",
            SefazServico.EVENTO,             "/ws/nferecepcaoevento4.asmx")),

    /** SVC-AN (Ambiente Nacional, Receita/Serpro), contingência. Não tem inutilização. */
    SVC_AN(
        Map.of(PRODUCAO, "https://www.svc.fazenda.gov.br",
               HOMOLOGACAO, "https://hom.svc.fazenda.gov.br"),
        Map.of(
            SefazServico.STATUS_SERVICO,     "/NFeStatusServico4/NFeStatusServico4.asmx",
            SefazServico.AUTORIZACAO,        "/NFeAutorizacao4/NFeAutorizacao4.asmx",
            SefazServico.RET_AUTORIZACAO,    "/NFeRetAutorizacao4/NFeRetAutorizacao4.asmx",
            SefazServico.CONSULTA_PROTOCOLO, "/NFeConsultaProtocolo4/NFeConsultaProtocolo4.asmx",
            SefazServico.EVENTO,             "/NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx"));

    private final Map<SefazAmbiente, String> hosts;
    private final Map<SefazServico, String> paths;

    Autorizador(Map<SefazAmbiente, String> hosts, Map<SefazServico, String> paths) {
        this.hosts = new EnumMap<>(hosts);
        this.paths = new EnumMap<>(paths);
    }

    public Optional<URI> uri(SefazAmbiente ambiente, SefazServico servico) {
        String path = paths.get(servico);
        return path == null ? Optional.empty() : Optional.of(URI.create(hosts.get(ambiente) + path));
    }
}
