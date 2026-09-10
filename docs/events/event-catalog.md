# Catálogo de eventos

Formato de envelope padrão (convenção global em `base.properties`):
```json
{ "type": "<EventType>", "correlationId": "<sagaId>", "...": "resto do payload" }
```

## Domínio: Cashback

| Evento | Publicado por | Consumido por | Payload (além de type/correlationId) |
|---|---|---|---|
| `TransactionValidated` | `transaction-ingest-service` | motor (rota `CalculateCashback`) | `userId`, `amountCents`, `currency`, `validatedAtMs` |
| `CashbackApplied` | `cashback-service` (ou `cashback-outbox-jpa`) | motor (rota `SagaCompleted`) | `userId`, `cashbackCents`, `appliedAtMs` |

Comandos (mesma forma de envelope, publicados pelo motor):

| Comando | Publicado pelo motor em resposta a | Consumido por |
|---|---|---|
| `CalculateCashback` | `TransactionValidated` | `cashback-service` |
| `SagaCompleted` | `CashbackApplied` | (nenhum consumer ainda -- fim da saga) |

## Demo genérica: OrderSaga (não usada por nenhum domínio real)

| Evento | Consumido por | Observação |
|---|---|---|
| `OrderCreated` | motor (rotas `ReservePayment`,`ConfirmInventory`) | nenhum serviço real implementado, só demo |
| `InventoryConfirmed` | motor (rota `SagaCompleted`) | |
| `InventoryFailed` | motor (rota `RollbackPayment`) | |
| `PaymentRolledBack` | motor (rota `SagaFailed`) | fecha o ciclo de compensação |

## Domínio: NF-e (planejado, ainda não implementado)

Ver `docs/roadmap/roadmap_rediscache_event_driven_nfe.pdf` para o desenho original
(`NFE_RECEBIDA`, `XML_VALIDADO`, `EVENTO_PERSISTIDO`, etc.). Ainda não há um
`orchestrator.nfe.properties` -- ao criar, adicionar a tabela correspondente aqui.
