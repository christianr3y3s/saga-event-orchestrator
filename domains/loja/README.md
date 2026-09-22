# Domínio: Loja

Segundo domínio de negócio no monorepo (o primeiro é `cashback`), usando o mesmo motor
genérico (`engine/saga-orchestrator-rust`) sem nenhuma mudança nele — só um
`.properties` novo e os serviços que implementam os consumers/producers dos eventos
declarados (veja `docs/adding-a-domain.md`).

## Fluxo

```
                    PedidoCriado
                         │
                         ▼
                 [ ReservarEstoque ]
                         │
        ┌────────────────┴────────────────┐
        ▼                                  ▼
 EstoqueReservado                  EstoqueIndisponivel
        │                                  │
        ▼                                  ▼
[ ProcessarPagamento ]                  Failed
        │
   ┌────┴─────┐
   ▼          ▼
PagamentoRecusado    PagamentoAprovado
   │                       │
   ▼                       ▼
[ LiberarEstoque ]   [ ExpedirPedido ] → SagaCompleted
   │
   ▼
EstoqueLiberado
   │
   ▼
 Failed
```

FSM declarada em `orchestrator.loja.properties` (validada no bootstrap do motor —
veja `engine/saga-orchestrator-rust/src/fsm.rs`). Ao contrário do gap documentado de
propósito em `orchestrator.cashback.properties`, aqui `Compensando` tem uma transição de
saída completa (`EstoqueLiberado → Failed`) — sem isso o motor recusaria o boot.

## O que está construído

### `loja-inventory-service`

Reserva/libera estoque de forma transacional (JPA + outbox), no mesmo padrão comprovado
em `domains/cashback/cashback-outbox-jpa`, mas com duas diferenças importantes:

1. **Múltiplos itens, tudo ou nada.** Um pedido pode ter vários SKUs — a reserva só é
   aplicada se TODOS os itens tiverem estoque suficiente. A checagem de disponibilidade é
   feita para a lista inteira antes de decrementar qualquer item, evitando ter que desfazer
   manualmente um decremento parcial. A garantia real de atomicidade continua sendo o
   `@Transactional` do método (se a gravação do outbox falhar, o Spring desfaz também os
   decrementos já aplicados nesta chamada).

2. **Constraint UNIQUE composta.** O cashback usa `UNIQUE(correlationId)` porque cada
   transação gera no máximo um evento de saída durante toda a sua vida. Aqui não: o mesmo
   `correlationId` (o pedido) pode gerar `EstoqueReservado` e, depois, se o pagamento for
   recusado, `EstoqueLiberado` (compensação) — dois eventos legítimos para o mesmo
   correlationId. Por isso a constraint é `UNIQUE(correlationId, eventType)`: continua
   bloqueando a duplicata de um mesmo evento sob concorrência, mas permite o par
   reserva→liberação. Veja o comentário em `InventoryOutboxEvent` e o teste
   `differentEventTypesForSameCorrelationIdAreBothAllowedByTheConstraint`.

Ao contrário de `cashback-outbox-jpa` (que existe só para provar a atomicidade,
sem listener Kafka), este serviço está conectado de ponta a ponta:
`InventoryCommandListener` consome `ReservarEstoque`/`LiberarEstoque` do orquestrador,
delega para `InventoryReservationService`, e `InventoryOutboxPublisher` publica os
eventos de saída de volta.

**Contrato de payload** que `ReservarEstoque` e `LiberarEstoque` precisam carregar
(veja `ReservationCommandData`):
```json
{ "orderId": "order-123", "items": [ { "sku": "SKU-A", "quantity": 2 } ] }
```
Isso importa para quem for implementar o `payment-service`: ao emitir
`PagamentoRecusado`, o payload precisa ecoar `orderId` + `items` do pedido original —
o motor só encaminha o `data` do evento de entrada para o comando de saída, não faz
lookup em nenhum banco.

### API HTTP (somente leitura)

`loja-inventory-service` ganhou `spring-boot-starter-web` só para expor uma consulta de
estoque para o front-end React (`domains/loja/loja-inventory-web`, ver abaixo):

```
GET /estoque         -> lista todos os itens: [{ "sku": "SKU-A", "availableQuantity": 10 }, ...]
GET /estoque/{sku}   -> um item, ou 404 se o SKU não existir
```

**Decisão deliberada: nada além de leitura.** `EstoqueController` não tem nenhum
`@PostMapping`/`@PutMapping`. Reservar/liberar estoque continua exclusivamente via Kafka
(`InventoryCommandListener`, dentro da saga) — expor isso por HTTP permitiria mudar o
estoque por fora da saga, quebrando a garantia de idempotência/compensação que o outbox dá
(a mesma razão pela qual `cashback-outbox-jpa` também não tem API de escrita). Se um dia for
preciso reservar estoque fora da saga (ex.: um ajuste manual de operação), isso deve ser um
comando novo publicado no tópico do orquestrador, não um endpoint REST.

CORS liberado para `/estoque/**` (`CorsConfig`, mesmo padrão do `logistica-rota-service`),
configurável por `app.cors.allowed-origins`/`APP_CORS_ALLOWED_ORIGINS`.

### `loja-inventory-web`

Front-end React (uma tela: listar estoque + buscar por SKU), consumindo só `GET /estoque`
e `GET /estoque/{sku}` acima -- sem nenhuma tela de reserva/liberação, de propósito. Testes
com Jasmine, mesma combinação (jsdom + Testing Library) do `logistica-rota-web`. Mesma
limitação de ambiente do módulo irmão: `npm install` não foi possível aqui (registro do npm
e CDNs bloqueados neste sandbox) -- a lógica sem React (`src/api/estoqueApi.js`) foi
verificada com Node puro, mas rode `npm install && npm test` numa máquina normal antes de
considerar isto pronto (ver `loja-inventory-web/README.md`).

## O que ainda não está implementado (próximos passos)

Seguindo a mesma lógica de isolamento do `nfe-generator` (SEFAZ isolada num serviço
próprio): cada peça de instabilidade externa fica isolada, sem vazar para o motor nem
para os outros serviços do domínio.

- **`order-service`** — recebe o pedido do cliente (REST/UI), publica `PedidoCriado`
  com `orderId` + `items`, e mais tarde reage a `SagaCompleted`/`SagaFailed` para
  atualizar o status visível ao cliente.
- **`payment-service`** — consome `ProcessarPagamento`, integra com o gateway de
  pagamento real (Stripe/Pagar.me/etc.), publica `PagamentoAprovado` ou
  `PagamentoRecusado` (ecoando `orderId`+`items`, como descrito acima).
- **`shipment-service`** — consome `ExpedirPedido`, dispara a etiqueta/coleta.
  Observação: no fluxo atual, `ExpedirPedido` e `SagaCompleted` são emitidos juntos
  (`route.PagamentoAprovado.emit=ExpedirPedido,SagaCompleted`) — expedição é
  "fire-and-forget" fora da saga, não faz parte do caminho crítico de consistência
  (se falhar, é um problema operacional a reconciliar depois, não motivo pra desfazer
  pagamento/reserva).

## Rodar os testes

```bash
cd domains/loja/loja-inventory-service
mvn test                        # só os testes Mockito (rápidos, sem H2/Kafka)
mvn test -DexcludedGroups=      # inclui o teste de integração (@DataJpaTest + H2)
```

## Rodar a aplicação

```bash
docker compose -f docker/docker-compose.dev.yml up -d --wait
./scripts/create-topics.sh domains/loja/topics.txt
./scripts/run-domain.sh loja        # sobe o orquestrador Rust pra este domínio
cd domains/loja/loja-inventory-service && mvn spring-boot:run
```

## Limitações conhecidas

- Sem `order-service`/`payment-service`/`shipment-service` implementados ainda — dá pra
  testar o `loja-inventory-service` isoladamente publicando comandos `ReservarEstoque`/
  `LiberarEstoque` manualmente no tópico (`kafka-console-producer`), mas o fluxo
  ponta-a-ponta real depende dessas três peças.
- Mesma limitação de paginação do outbox do cashback: `findTop50` sem índice dedicado em
  `(status, id)` — ok para protótipo, revisar antes de produção com volume alto.
