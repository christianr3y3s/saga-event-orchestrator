# saga-event-orchestrator

Motor de orquestração de sagas (Rust + Kafka, config-driven) e os domínios que rodam
sobre ele. O motor não conhece nenhum domínio específico — cashback, NF-e ou qualquer
outro futuro são só arquivos `.properties` + serviços consumindo/publicando eventos.

## Estrutura

```
.
├── engine/
│   └── saga-orchestrator-rust/    # motor genérico -- não sabe o que é cashback ou NF-e
├── domains/
│   └── cashback/                  # implementado e funcional
│       ├── transaction-ingest-service/
│       ├── cashback-service/
│       ├── cashback-outbox-jpa/
│       └── orchestrator.cashback.properties
├── docker/
│   └── docker-compose.dev.yml     # infra compartilhada (Kafka, Zookeeper, Redis)
├── scripts/
│   ├── create-topics.sh           # cria tópicos a partir de domains/<nome>/topics.txt
│   └── run-domain.sh              # combina base.properties + o domínio e roda o motor
└── docs/
    ├── architecture/
    ├── events/                    # catálogo de eventos por domínio
    ├── roadmap/                   # roadmap original (NF-e)
    └── adding-a-domain.md         # como plugar um domínio novo
```

## Por que essa separação

O motor Rust é genérico de propósito — lê eventos, roteia por config, valida a FSM
declarada, republica comandos. Cashback e NF-e são só dois "clientes" desse motor com
contratos de evento diferentes. Isolar assim significa que uma correção no motor
(ex.: o validador de FSM, a reidratação do state store) beneficia os dois domínios ao
mesmo tempo, sem precisar de cherry-pick entre branches.

A peça verdadeiramente específica de cada domínio fica isolada também: no cashback é o
cálculo/aplicação do cashback; no NF-e (ainda não implementado) vai ser a integração com
a SEFAZ. O motor não sabe que nenhuma dessas coisas existe.

## Rodando um processo por domínio (padrão atual)

Cada domínio tem seu próprio `.properties`, seu próprio consumer group efetivo (definido
ao combinar com `base.properties`) e roda como um processo separado do motor:
```bash
./scripts/run-domain.sh cashback
```

Isso é deliberado por enquanto: mais simples de raciocinar sobre falha (um domínio caindo
não derruba o outro). Se algum domínio crescer o suficiente para justificar rodar os dois
no mesmo processo (uma FSM por `saga.<Tipo>` no mesmo arquivo combinado), isso já é
suportado pelo motor sem mudança de código — é só concatenar os dois `.properties` de
domínio antes do `base.properties`.

## Começando
```bash
cd docker && docker compose -f docker-compose.dev.yml up -d && cd ..
./scripts/create-topics.sh domains/cashback/topics.txt
./scripts/run-domain.sh cashback
```
Depois, veja `domains/cashback/README.md` pra subir os serviços Java e rodar o teste
ponta a ponta.

## Adicionando um domínio novo (ex.: NF-e)
Veja `docs/adding-a-domain.md`. O roadmap original do domínio NF-e está em
`docs/roadmap/roadmap_rediscache_event_driven_nfe.pdf`.
