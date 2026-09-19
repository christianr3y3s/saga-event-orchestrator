# `logistica-importacao-service`

Recebe planilhas Excel de entregas/custos, valida linha a linha e grava no Postgres. Essa
base serve a dois consumidores (as tarefas 2 e 3 pedidas):

1. **Calibração de consumo**: `GET /historico/consumo-medio` devolve a média do consumo
   REAL medido (quando há histórico suficiente) em vez do valor nominal fixo da tabela de
   `logistica-rota-service` -- é o "cálculo otimizado" pedido em cima da base de entregas.
2. **Dataset para treinamento de IA**: `GET /historico/dataset.csv` exporta o histórico
   inteiro em CSV, pronto para um pipeline externo de treinamento consumir.

Serviço síncrono (Spring Boot, Java 17, Maven), porta `8086`. Sem Kafka/saga de propósito
-- é ingestão + consulta, mesma razão de `logistica-rota-service` não ter FSM.

## Contrato de colunas da planilha

Cabeçalho na primeira linha da primeira aba. Nomes de coluna são normalizados (sem
acento, minúsculo, espaços viram `_`) antes de comparar, então `"Carga (Toneladas)"`,
`"carga_toneladas"` e `"CARGA TONELADAS"` são todos aceitos como a mesma coluna. **Ordem
das colunas é livre.**

| Coluna | Obrigatória | Formato | Observação |
|---|---|---|---|
| `data` | sim | `AAAA-MM-DD`, `DD/MM/AAAA` ou célula de data do Excel | data da entrega |
| `caminhao` | sim | `RODOTREM`, `CARRETA_4_EIXOS` ou `CARRETA_30T` (case-insensitive, espaço vira `_`) | mesmos nomes de `TipoCaminhao` em `logistica-rota-service` |
| `carga_toneladas` | sim | número ≥ 0 | aceita `,` ou `.` como separador decimal |
| `distancia_km` | sim | número > 0 | |
| `consumo_kml` | não | número > 0 | consumo REAL medido nesta entrega -- é o dado que calibra `/historico/consumo-medio` |
| `custo_diesel` | não | número ≥ 0 | |
| `custo_operacional` | não | número ≥ 0 | |
| `pedagios` | não | número ≥ 0 | |
| `frete_cobrado` | não | número ≥ 0 | |
| `origem` | não | texto livre | |
| `destino` | não | texto livre | |

Uma linha totalmente em branco é ignorada (não conta nem como importada, nem como
rejeitada). Uma planilha sem as 4 colunas obrigatórias no cabeçalho é recusada inteira,
com a lista do que falta. Faltando uma coluna obrigatória **numa linha só**, só aquela
linha é rejeitada -- a importação continua para as demais.

## Endpoints

- `POST /importacoes/planilhas` (multipart, campo `arquivo`) -- importa a planilha.
  Devolve `{ arquivo, totalLinhas, importadas, rejeitadas, erros: [{linha, motivo}],
  alertasConsumo: [{linha, caminhao, consumoKmL}] }`. `alertasConsumo` reaproveita a
  mesma observação de controle de `logistica-rota-service` (consumo medido < 2,5 km/L) --
  aqui é só um aviso informativo na resposta, não um bloqueio da importação.
- `GET /historico` -- todo o histórico importado, como JSON. Sem paginação ainda (ver
  "Limitações" abaixo).
- `GET /historico/dataset.csv` -- o mesmo histórico em CSV, para consumo por um pipeline
  de treinamento de IA.
- `GET /historico/consumo-medio?caminhao=X&cargaToneladas=Y` -- consumo calibrado.
  `fonte` na resposta diz se veio de `"historico"` (média de entregas reais com esse
  caminhão, no mesmo bucket vazio/carregado) ou `"tabela_nominal"` (ainda sem dados
  reais suficientes).

## Banco de dados

Postgres real (ver `docker/docker-compose.dev.yml`, serviço `postgres`, porta `5432`,
banco/usuário/senha `logistica`). Uma tabela: `entrega_historico`, com todas as colunas
da planilha mais `arquivo_origem`, `linha_planilha` e `importado_em` para rastreabilidade
(de qual arquivo e linha cada registro veio). `ddl-auto: update` -- ok para protótipo;
antes de uma migração real, trocar por Flyway/Liquibase (mesma ressalva já registrada em
`cashback-outbox-jpa`/`loja-inventory-service`).

## Revisão de fronteiras (Clean Code / SOLID) desta rodada

**Dependency Rule invertida (corrigido).** `PlanilhaImportService` e
`HistoricoConsumoService` (pacote `dominio`) importavam e devolviam tipos de `api.dto`
diretamente (`ImportacaoResumo`, `AlertaConsumo`, `LinhaRejeitada`, `ConsumoMedioResponse`)
-- o domínio dependendo da camada de transporte, direção contrária à regra de dependência
(camadas internas não conhecem as externas) e inconsistente com o padrão já correto em
`logistica-rota-service` (onde o domínio devolve seus próprios tipos e os DTOs de API é que
mapeiam de volta via `deDominio(...)`).

Corrigido criando os equivalentes no pacote `dominio`: `ResumoImportacao`,
`LinhaRejeitadaImportacao`, `AlertaConsumoMedido` e `ConsumoMedio` (mais o enum
`FonteConsumo`, substituindo o `String` solto `"historico"`/`"tabela_nominal"` -- primitive
obsession que deixava o valor livre para digitar errado). Os dois serviços agora devolvem só
tipos do próprio domínio; `api.dto.ImportacaoResumo`, `AlertaConsumo`, `LinhaRejeitada` e
`ConsumoMedioResponse` viraram wrappers finos com `deDominio(...)`, e os controllers chamam
esse mapeamento antes de responder. O contrato HTTP não mudou -- `fonte` continua sendo
`"historico"`/`"tabela_nominal"` como string no JSON; só a representação interna virou enum.

## Isolamento de `logistica-rota-service`

Este serviço **duplica** um pedaço pequeno da tabela de modelos de caminhão
(`ModelosCaminhaoConhecidos`) em vez de depender do módulo `logistica-rota-service`. É a
mesma convenção de isolamento entre domínios já usada no resto do repositório (cashback e
loja também não compartilham código) -- o preço é que, se a tabela de consumo mudar, tem
que atualizar as duas cópias (aqui e em `TipoCaminhao`). Se isso incomodar conforme o
projeto cresce, o próximo passo natural é extrair um módulo `logistica-domain-comum`
consumido pelos dois, mas isso é uma decisão de arquitetura que vale tomar
deliberadamente, não de forma silenciosa.

**A integração de fato entre os dois serviços (rota-service consultando
`/historico/consumo-medio` para usar consumo calibrado em vez do nominal) ainda não foi
feita** -- hoje são dois serviços HTTP independentes. Ligar os dois é o próximo passo
óbvio, mas não estava no escopo desta rodada.

## Testes

- **Unitários**: `ModelosCaminhaoConhecidosTest`, `CelulaLeitorTest` (parsing de número
  BR/EN e data em vários formatos), `PlanilhaImportServiceTest` (parsing completo com
  planilha construída em memória via Apache POI, repositório mockado com Mockito --
  cobre linha em branco ignorada, alerta de consumo baixo, linha rejeitada sem travar as
  demais, planilha sem coluna obrigatória; agora contra os tipos de domínio
  `ResumoImportacao`/`LinhaRejeitadaImportacao`/`AlertaConsumoMedido`),
  `HistoricoConsumoServiceTest` (fallback para tabela nominal, média por bucket
  vazio/carregado; agora contra `ConsumoMedio`/`FonteConsumo`) e `DatasetCsvExporterTest`
  (escaping RFC 4180).
- **Integração** (`ImportacaoIntegrationTest`, `@Tag("integration")`): sobe o contexto
  Spring completo, servidor HTTP real, e faz upload multipart de verdade para
  `/importacoes/planilhas`, depois lê de volta por `/historico` e `/historico/dataset.csv`
  -- ponta a ponta, sem mock.

### Rodando localmente

```bash
docker compose -f docker/docker-compose.dev.yml up -d postgres --wait
cd domains/logistica/logistica-importacao-service
mvn test
mvn spring-boot:run   # sobe em :8086
```

> **Build não verificado neste ambiente**: mesma limitação de rede dos outros módulos
> (Maven Central bloqueado, 403). Além disso, aqui a lacuna é maior: as classes deste
> serviço dependem de Apache POI, Jakarta Persistence e Spring, nenhum dos quais está
> disponível localmente para compilar sem Maven -- diferente de `logistica-rota-service`
> e `nfe-sefaz-integration`, não foi possível compilar NENHUMA classe deste módulo nesta
> sessão, nem com `javac` puro. O que foi verificado à parte, extraindo a lógica pura para
> fora das classes que dependem de POI/JPA/Spring: a tabela nominal de fallback
> (`ModelosCaminhaoConhecidos`), o parser de número BR/EN (a lógica de
> `CelulaLeitor.numeroOuNulo`) e o escaping de CSV (`DatasetCsvExporter.campo`) -- todos
> bateram. A leitura de planilha via POI, a camada JPA e os controllers **não foram
> exercitados de forma alguma**. Rode `mvn test` no seu ambiente antes de confiar nisto.

## Limitações conhecidas (fora de escopo desta rodada)

- Sem paginação em `GET /historico` -- numa base grande isso precisa mudar antes de virar
  produto real.
- Sem autenticação/autorização em nenhum endpoint.
- `consumo-medio` usa só dois buckets (vazio/carregado); não interpola por faixa de carga.
- Nenhuma feature engineering para IA acontece aqui -- o CSV é o histórico bruto; limpeza,
  normalização e seleção de features ficam para o pipeline de treinamento.
