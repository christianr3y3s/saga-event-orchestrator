# Saga Orchestrator (Rust + Kafka) — config-driven

Este projeto é um **orquestrador genérico** (control-plane) para fluxos estilo **Saga**,
dirigido por **eventos Kafka**. A ideia é: **ninguém mexe neste código pra adicionar um
domínio novo**; fluxo, eventos, rotas, timeouts e FSM ficam em arquivos `.properties`.

Este módulo não conhece cashback, NF-e, nem nenhum outro domínio — veja `../../domains/`
para as implementações reais. Este README cobre só o motor rodando sozinho (demo).

## O que já vem pronto
- Consumer Kafka async (rdkafka + tokio)
- Producer Kafka async (idempotente)
- Leitura de properties (parser simples, sem libs mágicas)
- State Store via **tópico compactado** (`_orchestrator.state`), reidratado no boot
- DLQ (`_orchestrator.dlq`) e Retry (`_orchestrator.retry`) configuráveis
- Validador de FSM declarativa no bootstrap (`src/fsm.rs`) -- veja a seção mais abaixo

## Pré-requisitos
- Docker (para Kafka local)
- Rust 1.65+ (rustup) — o código usa `let-else`
- `cmake` e um toolchain C (necessários para compilar `librdkafka` via o feature `cmake-build` do crate `rdkafka`)

## Subir Kafka local
A infra (Kafka/Zookeeper/Redis) agora é compartilhada entre domínios -- veja
`../../docker/docker-compose.dev.yml` na raiz do repositório:
```bash
cd ../../docker && docker compose -f docker-compose.dev.yml up -d
```

## Rodar o motor sozinho (demo, sem nenhum domínio real)
```bash
cargo run   # usa orchestrator.properties.example por padrão (saga OrderSaga de demonstração)
```

## Rodar com um domínio real (ex.: cashback)
As declarações de cada domínio (eventos, comandos, rotas, FSM) vivem em
`domains/<nome>/orchestrator.<nome>.properties` e precisam ser combinadas com este
`base.properties` (que só tem config de Kafka + convenções globais, sem nada de domínio):
```bash
../../scripts/run-domain.sh cashback
```
(o script só concatena `base.properties` + o `.properties` do domínio e roda `cargo run`
com `ORCH_CONFIG` apontando pro arquivo combinado -- veja o script pra entender exatamente
o que ele faz, não tem mágica.)

## Teste rápido: produzir evento
Use o console producer do Kafka (dentro do container):
```bash
docker exec -it kafka bash
kafka-console-producer --bootstrap-server localhost:9092 --topic orders.created
```
Cole um JSON (precisa ter `type` e `correlationId` por padrão):
```json
{"type":"OrderCreated","correlationId":"order-123","payload":{"x":1}}
```

Você verá no log do orquestrador:
- evento recebido
- comandos publicados (definidos no properties)
- estado atualizado no `_orchestrator.state`

## Onde mudar o comportamento
- `base.properties` — config de Kafka e convenções globais (compartilhado por todo domínio)
- `orchestrator.properties.example` — demo autocontida (OrderSaga), não usada por domínio real
- `../../domains/<nome>/orchestrator.<nome>.properties` — eventos/comandos/rotas/FSM de cada domínio

## Observações
- Este template mantém o motor genérico. Você pode evoluir as regras para uma FSM (máquina de estados) declarativa usando o mesmo arquivo.

## Correções aplicadas nesta revisão
- **Reidratação do state store**: ao iniciar, o orchestrator agora lê `_orchestrator.state` do início antes de consumir eventos de negócio, reconstruindo o cache in-memory (`version`, status, último evento por saga). Antes, o cache começava sempre vazio e o comentário no código dizia o contrário do que o código fazia.
- **Commit manual de offset**: `kafka.enable.auto.commit` virou informativo; o offset só avança depois que a mensagem foi processada com sucesso (roteada, mandada pra DLQ, ou ignorada por política). Falha de publish agora não comita, e a mensagem é reentregue.
- **`error.on.missing.event.type` e `error.on.missing.correlation` agora são de fato aplicadas** — antes essas mensagens só geravam um `warn!` e eram descartadas, sem passar pela DLQ configurada.
- **Roteamento de comandos não aborta mais no meio**: se um comando de uma rota falhar ao publicar, os demais ainda são tentados; só então a mensagem inteira é marcada como falha (offset não comitado).
- **Status da saga deixou de ser fixo em `"InProgress"`**: agora é derivado dos comandos emitidos pela rota (heurística por nome: `SagaCompleted`→Completed, `SagaFailed`→Failed, comandos com `rollback`/`compensate`→Compensating).
- **Producer idempotente** (`enable.idempotence=true`) para evitar duplicação em retries internos do rdkafka.
- **Validador de FSM no bootstrap** (`src/fsm.rs`): se você declarar uma FSM (`saga.<Tipo>.start/.end/.states/.state.*.on.*`), o processo valida no início e recusa subir se houver um **dead end** (estado sem saída que não é end state) ou um **trap loop** (ciclo que nunca alcança nenhum end state). Foi assim que achamos e fechamos o gap real do `RollbackPayment` sem confirmação de volta — veja `route.PaymentRolledBack.emit=SagaFailed` no properties.

## Limitações conhecidas (próximos passos)
- A reidratação usa uma heurística de "silêncio por N segundos" para decidir que chegou ao fim do tópico, em vez de comparar contra o high watermark de cada partição — adequado para um tópico de estado pequeno, mas vale trocar por comparação exata de watermark se o volume crescer.
- Não há um motor real de compensação (rastreamento de passos executados por saga para desfazer em ordem reversa); o roteamento continua estático (1 evento de falha → 1 comando configurado). O validador de FSM garante que os estados declarados fecham o ciclo, mas não substitui um motor de compensação de verdade.
- Redelivery após falha de publish pode duplicar comandos que já tinham sido publicados com sucesso antes do comando que falhou; o producer idempotente cobre duplicação de retry de rede, mas o *consumidor* de cada comando ainda precisa ser idempotente por `correlationId`.
- O validador de FSM é opcional e desacoplado do roteamento real (`route.*.emit`) — nada impede declarar uma FSM que não bate com as rotas de verdade. Um próximo passo natural é gerar a FSM a partir das rotas automaticamente, em vez de exigir as duas declarações em paralelo.

# Security and Identity

This repository documents **application-level identity decisions explicitly**.

Events and commands may carry a JSON Web Token (JWT) inside the payload in order to
express logical identity and authorization in a language-agnostic way.

Transport- and broker-level security (TLS, SASL, ACLs) are intentionally treated as
separate concerns and are expected to be enforced by the messaging infrastructure.