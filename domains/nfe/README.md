# Domínio: NF-e

Ver a seção "Sobre o domínio NF-e" em `docs/adding-a-domain.md` para o roadmap completo
(Event Store → Saga → Read Model → Cache Projection → Cliente Quarkus → Resilience) e por
que a integração com a SEFAZ deve ficar isolada num serviço próprio. Este diretório
contém a primeira peça desse isolamento.

## O que está construído: `nfe-sefaz-integration`

Biblioteca Java pura (sem Spring, sem Kafka -- um `.jar` comum) que resolve **qual
endpoint SOAP chamar**, dado a UF do emitente, o ambiente (produção/homologação), o
serviço (autorização, status, evento, etc.) e o tipo de emissão (normal ou contingência
SVC-AN). É o ponto de entrada que o futuro cliente SOAP (assinatura XML + envio,
mencionado como stub pendente de certificado no `nfe-generator`) vai consumir --
**este módulo não assina nem envia nada**, só decide a URL.

### Classes

- `Uf` -- as 27 UFs com o código IBGE (`cUF`) de cada uma.
- `SefazAmbiente` -- produção (`tpAmb=1`) / homologação (`tpAmb=2`).
- `SefazServico` -- status do serviço, autorização, retorno de autorização, consulta de
  protocolo, inutilização, evento (cancelamento/CC-e/manifestação).
- `Autorizador` -- os autorizadores conhecidos hoje: `SVRS`, `SEFAZ_SP` e `SVC_AN`
  (contingência, Ambiente Nacional/Serpro). Cada um sabe montar a URL completa por
  ambiente e serviço.
- `AutorizadorResolver` -- a regra de roteamento (ver abaixo).

### A regra de roteamento pedida -- e por que ela é provisória

A tarefa pedida foi literal: **UF = PA usa SVRS; qualquer outra UF usa SEFAZ-SP.**
`AutorizadorResolver` implementa exatamente isso, e os testes (`AutorizadorResolverTest`)
verificam a regra para as 27 UFs.

**Isto não é a regra real da NF-e**, e vale registrar isso claramente antes que vire
suposição silenciosa:

- A SEFAZ-SP só autoriza NF-e de emitentes **cadastrados em São Paulo**. Cadastrar um
  emitente de qualquer UF que não seja PA ou SP e mandar a nota pra SEFAZ-SP vai falhar
  na autorização (o autorizador rejeita nota de emitente de outra UF).
- Na realidade, cada UF tem seu autorizador contratado -- a SVRS hoje cobre bem mais
  estados que só o PA (AC, AL, AP, BA, CE, DF, ES, GO, MA, MS, MT, PA, PB, PE, PI, RJ, RN,
  RO, RR, SC, SE, TO -- confira a lista atual no Portal Nacional da NF-e, ela muda de
  tempos em tempos), e alguns estados grandes (SP, AM, MG, PR, RS, entre outros) mantêm
  autorizador próprio, não SVRS nem SEFAZ-SP.

A regra pedida "funciona" hoje porque o catálogo de clientes deste projeto é só PA e SP.
`AutorizadorResolver` foi desenhado para que crescer isso seja uma mudança pequena: o
construtor recebe um `Map<Uf, Autorizador>` completo em vez do default de uma entrada só
-- antes de cadastrar um emitente de qualquer outra UF, preencha a tabela completa (as
outras 25 entradas) em vez de confiar no fallback para SEFAZ-SP.

## O que NÃO está construído (fora de escopo desta rodada)

- **Cliente SOAP de verdade** (montar o envelope, assinar com certificado ICP-Brasil
  A1/A3, enviar, interpretar retorno) -- é o stub pendente mencionado no `nfe-generator`
  (ver memória do projeto). Este módulo só resolve a URL.
- **Consumers/producers Kafka** do domínio NF-e (`XmlValidado` → `NfeAutorizada`/
  `NfeRejeitada`) e o `orchestrator.nfe.properties` correspondente -- ainda não
  implementados, seguem o roadmap do PDF em `docs/roadmap/`.
- **Confirmação das URLs contra o Portal Nacional.** As URLs em `Autorizador` (SVRS,
  SEFAZ-SP e SVC-AN) foram escritas de memória, não conferidas ao vivo nesta sessão --
  confirme cada uma no Portal Nacional da NF-e antes de apontar para produção real. Os
  serviços usam SOAP 1.2 com TLS mútuo (certificado do emitente no handshake).

## Testes

`AutorizadorTest`, `UfTest` e `AutorizadorResolverTest` -- unitários, sem I/O. Cobrem a
montagem de URL por autorizador/ambiente/serviço, os 27 códigos IBGE, a regra PA→SVRS /
demais UFs→SEFAZ-SP explicitamente para as 26 UFs restantes, o roteamento de contingência
(sempre SVC-AN) e os erros (tpEmis inválido, serviço indisponível no autorizador).

> **Build não verificado neste ambiente**: mesmo motivo do `logistica-rota-service` --
> Maven Central bloqueado pela política de rede desta sessão. A lógica foi conferida à
> parte com `javac`/`java` puro (sem JUnit): todos os checks (URLs, códigos IBGE, a regra
> de roteamento para as 27 UFs, contingência, erros) passaram. `mvn test` continua sendo
> necessário antes de considerar isto pronto.
