# Domínio: Cashback

Três serviços (dois Java/Spring Boot + o motor Rust compartilhado) que juntos implementam
a saga de cashback. Nenhum código do motor genérico (`../../engine/`) conhece cashback —
tudo que é específico deste domínio vive aqui.

## Desenho do fluxo

```
POS (compra no crédito)
   │  publica rápido, sem esperar nada
   ▼
payments.transaction.created  (tópico "cru")
   │
   ▼
[transaction-ingest-service]  ── idempotência + outbox no Redis ──
   │  publica evento limpo
   ▼
transactions.validated
   │
   ▼
[engine/saga-orchestrator-rust]  (rodando com orchestrator.cashback.properties)
   │  route.TransactionValidated.emit=CalculateCashback
   ▼
cashback.calculate
   │
   ▼
[cashback-service]  ── calcula %, credita saldo (idempotente), outbox no Redis ──
   │  publica confirmação
   ▼
cashback.applied
   │
   ▼
[engine/saga-orchestrator-rust]
   route.CashbackApplied.emit=SagaCompleted
```

`orchestrator.cashback.properties` (neste diretório) já tem tudo declarado — eventos,
comandos, rotas e a FSM da `CashbackSaga`. Nenhuma mudança no motor é necessária pra
adicionar ou alterar esse fluxo.

## Módulos deste domínio

- **`transaction-ingest-service`**: o caixa do supermercado não pode esperar o cashback
  ser calculado. Só recebe a transação, garante (via Redis) que não vai processar a
  mesma duas vezes e que não vai perder a transação se cair no meio do caminho, e
  repassa pro orquestrador. Rápido e burro de propósito.
- **`cashback-service`**: calcula o percentual e **efetiva na hora** (incrementa o saldo
  do usuário), guardado por uma trava de idempotência no Redis.
- **`cashback-outbox-jpa`**: segunda implementação do "efetivar cashback", com outbox
  transacional de verdade (JPA + H2, mesma transação SQL para saldo e evento) em vez do
  outbox best-effort em Redis. Veja o README dele para a comparação completa.

Os dois primeiros usam o mesmo padrão: **claim de idempotência + outbox** no Redis — se
o processo cair entre "já processei" e "já publiquei", um job agendado republica sem
duplicar nem perder nada.

## Pré-requisitos
- Docker
- Java 17+ e Maven
- Rust 1.65+ (para rodar o motor)

## Subir a infraestrutura (compartilhada entre domínios)
```bash
cd ../../docker && docker compose -f docker-compose.dev.yml up -d
cd ../..
./scripts/create-topics.sh domains/cashback/topics.txt
```

## Rodar tudo
```bash
# em terminais separados, a partir da raiz do repositório
./scripts/run-domain.sh cashback        # motor Rust, já combinado com este domínio

cd domains/cashback/transaction-ingest-service && mvn spring-boot:run
cd domains/cashback/cashback-service && mvn spring-boot:run
```

## Teste ponta a ponta
```bash
docker exec -it kafka bash
kafka-console-producer --bootstrap-server localhost:9092 --topic payments.transaction.created
```
Cole:
```json
{"transactionId":"tx-001","userId":"user-42","amountCents":15000,"currency":"BRL","createdAtMs":1000}
```

O que deve acontecer, em ordem:
1. `transaction-ingest-service` loga a transação recebida e publica em `transactions.validated`.
2. O orquestrador Rust loga o evento e emite o comando `CalculateCashback` em `cashback.calculate`.
3. `cashback-service` calcula 1% de R$150,00 (150 centavos) e credita `user-42`, publicando em `cashback.applied`.
4. O orquestrador recebe `CashbackApplied` e emite `SagaCompleted`.

Pra conferir o saldo direto no Redis:
```bash
docker exec -it redis redis-cli GET cashback:balance:user-42
```

## Limitações conhecidas (para evoluir depois)
- O outbox em Redis (`transaction-ingest-service`/`cashback-service`) é best-effort —
  cobre crash do processo, não perda de dados do próprio Redis. `cashback-outbox-jpa`
  já resolve isso com uma transação SQL de verdade; migrar os outros dois pra esse
  padrão é o próximo passo natural.
- "Efetivar na hora" (por transação) foi a opção escolhida — não há acúmulo/fechamento
  periódico.
- Redis roda como instância única aqui (sem réplica) — ponto único de falha para o
  saldo e para os outboxes que ainda usam Redis.
