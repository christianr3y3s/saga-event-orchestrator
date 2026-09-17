# Observabilidade: logs

## Revisão do que existia antes desta mudança

- **`RUST_LOG` não funcionava de verdade.** `main.rs` chamava
  `.with_env_filter("info")` passando uma string LITERAL. Apesar do nome do método
  sugerir leitura de variável de ambiente, isso só acontece quando o filtro é
  construído via `EnvFilter::try_from_default_env()`/`from_env()` — passar uma string
  direto hardcoda o nível pra sempre. Não dava pra baixar pra `debug` em produção sem
  recompilar o binário.
- **Saída em texto solto, não estruturada.** `tracing_subscriber::fmt()` (sem `.json()`)
  imprime uma linha de texto por evento. Os campos que os macros de log já capturam
  (`%saga_id`, `%event_type`, `%topic`, `%cmd`) ficavam visíveis na linha, mas não como
  dado estruturado recuperável sem regex.
- **Sem campo identificando o domínio.** Cada domínio roda como um processo separado
  do mesmo binário (`scripts/run-domain.sh <nome>`), mas como esse script combina
  `base.properties` + o do domínio num arquivo temporário (`mktemp`), o nome do arquivo
  não identifica o domínio — não havia como filtrar "só logs do domínio loja" num
  backend de log externo com múltiplos domínios rodando ao mesmo tempo.
- **Serviços Java sem nenhuma configuração de log** — padrão texto do Spring Boot,
  sem `correlationId` no MDC, sem JSON.

## O que mudou

### Orquestrador Rust (`engine/saga-orchestrator-rust`)
- `EnvFilter::try_from_default_env()` com fallback pra `"info"` — `RUST_LOG=debug`
  agora funciona de verdade.
- Saída em JSON (`tracing_subscriber::fmt().json().flatten_event(true)`) — o texto da
  mensagem fica em `message` na raiz do JSON, os demais campos do evento ficam junto.
- Novo campo `domain.name` no `.properties` de cada domínio (`orchestrator.cashback.properties`,
  `orchestrator.loja.properties`), lido em `config.rs` e usado para criar um span de
  topo (`tracing::info_span!("orchestrator", service = "saga-orchestrator", domain = ...)`)
  que envolve todo o `run()` do processo — todo log emitido carrega `span.domain` e
  `span.service` no JSON de saída.

### Serviços Java (exemplo em `loja-inventory-service`)
- Dependência `net.logstash.logback:logstash-logback-encoder` + `logback-spring.xml`
  configurando saída JSON com campos constantes `service`/`domain`.
- `InventoryCommandListener` põe `correlationId` no MDC assim que o comando é
  desserializado (`MDC.put`), e limpa em `finally` (**obrigatório** — o consumer Kafka
  reutiliza a mesma thread entre mensagens; sem o `finally`, o correlationId de uma
  mensagem vazaria pros logs de mensagens seguintes na mesma thread). Toda chamada de
  log dentro do processamento carrega `correlationId` automaticamente, sem precisar
  passar o valor em cada `log.info(...)`.

**Replicar nos outros 3 módulos Java** (`cashback-service`, `cashback-outbox-jpa`,
`transaction-ingest-service`): mesma dependência + `logback-spring.xml` (trocando só
`service`/`domain` nos `customFields`) + `MDC.put`/`MDC.remove` no listener de cada um.
Não apliquei nos quatro de uma vez porque este ambiente não tem acesso ao Maven Central
pra compilar e validar cada um (ver nota no fim) — prefiro um exemplo revisado a quatro
não verificados.

## Convenção de nome de arquivo (importante pro Promtail funcionar)

Como os serviços hoje rodam localmente (`cargo run`, `mvn spring-boot:run`), não como
containers Docker, o Promtail lê de arquivos bind-mountados em `./logs/` em vez de
escanear containers. Redirecione a saída de cada processo respeitando o padrão:

```bash
# Orquestrador Rust -- prefixo "orchestrator-" (job orchestrator-logs no promtail-config.yml)
./scripts/run-domain.sh cashback 2>&1 | tee -a logs/orchestrator-cashback.log
./scripts/run-domain.sh loja     2>&1 | tee -a logs/orchestrator-loja.log

# Serviços Java -- sufixo "-service.log" (job java-service-logs no promtail-config.yml)
cd domains/loja/loja-inventory-service
mvn spring-boot:run 2>&1 | tee -a ../../../logs/loja-inventory-service.log
```

Se/quando os serviços forem dockerizados, trocar os `scrape_configs` do Promtail por
`docker_sd_configs` (lê containers direto, sem precisar de `tee` manual) — está
documentado como próximo passo, não implementado agora pra não expandir escopo sem pedido.

## Ver os logs no Grafana (self-hosted, já incluído no docker-compose.dev.yml)

```bash
docker compose -f docker/docker-compose.dev.yml up -d --wait
```

Grafana em `http://localhost:3000` (login anônimo habilitado só pra dev — ver nota de
segurança abaixo), datasource Loki já provisionado automaticamente. Em **Explore**,
exemplos de LogQL:

```logql
# tudo do domínio loja
{domain="loja"}

# só erros de qualquer serviço
{job=~"saga-.*"} | json | level="ERROR"

# rastrear uma saga específica pelo correlationId através dos serviços Java
{job="saga-java-services"} | json | correlation_id="tx-102"

# falhas de publish no orquestrador (Rust)
{job="saga-orchestrator"} | json | message=~".*publish failed.*"
```

**Nota de segurança**: `GF_AUTH_ANONYMOUS_ENABLED=true` no compose é conveniência de
dev local — nunca usar essa configuração num Grafana exposto fora do laptop de
desenvolvimento.

## Trocar por Datadog em vez de Grafana/Loki

A parte cara de errar (formato do log) já está resolvida de forma neutra: os dois lados
(Rust e Java) emitem JSON estruturado em stdout/arquivo, sem nenhuma chamada de SDK de
vendor dentro do código da aplicação. Trocar de backend é só trocar o **coletor**:

1. Remover (ou manter, se quiser os dois em paralelo) o serviço `promtail` do compose.
2. Rodar o Datadog Agent apontando pra `./logs/*.log` — ele tem suporte nativo a
   "custom log collection" via arquivo, configurável por `datadog.yaml` +
   `conf.d/*.d/conf.yaml` com `logs: - type: file, path: /var/log/app/*.log,
   service: <nome>, source: java`/`source: rust`. O Agent já reconhece e faz parse de
   JSON de log automaticamente quando o campo é detectado.
3. Precisa de `DD_API_KEY` (conta Datadog paga) — não incluí isso no compose porque é
   uma credencial/custo real, não algo que deveria estar num arquivo versionado.

**Nota de custo/volume**: hoje cada evento processado pelo orquestrador gera no mínimo
3 linhas de log em nível `info` (evento recebido, comando publicado, estado
atualizado). Isso é webserver-verbosidade, não é caro pra um Loki self-hosted (grátis),
mas é ingestão real cobrada por GB num serviço pago como Datadog — vale revisar/reduzir
o nível pra `warn` em produção (via `RUST_LOG=warn`, que agora funciona de verdade) ou
configurar `log_level` amostrado antes de apontar pra um backend pago em volume alto.

## Limitações conhecidas

- Só `loja-inventory-service` tem o exemplo Java completo — os outros 3 módulos ainda
  precisam da mesma dependência + config (ver seção acima).
- Este ambiente de execução não tem acesso ao Maven Central (bloqueado pelo proxy),
  então as mudanças em `pom.xml`/`logback-spring.xml`/`InventoryCommandListener.java`
  foram revisadas manualmente (balanceamento de sintaxe, assinaturas cruzadas), não
  compiladas de fato. Rode `mvn compile` localmente antes de confiar em produção.
- Promtail lendo de arquivo (não de container) é uma solução de transição — assim que
  os serviços forem dockerizados, vale migrar pra `docker_sd_configs` (mais simples,
  sem precisar de `tee` manual em cada terminal).
- Sem alerting configurado no Grafana ainda (só visualização) — próximo passo natural
  seria um alerta em `level="ERROR"` sustentado por N minutos em qualquer `domain`.
