# cashback-outbox-jpa — Outbox transacional de verdade

Segunda implementação do "efetivar cashback", agora com garantia ACID de verdade (banco
relacional) em vez do outbox best-effort em Redis do `cashback-service`. Pensado pra
rodar primeiro em H2 em memória, exatamente como combinado, antes de trocar por Postgres.

## Por que isto existe ao lado do `cashback-service` (Redis)

O outbox em Redis (duas chaves separadas: claim de idempotência + saldo) é best-effort —
cobre o caso comum, mas não é atômico de verdade entre as duas escritas. Aqui, saldo e
evento de saída vivem na mesma transação SQL (`CashbackApplicationService.applyCashback`),
então uma falha no meio desfaz as duas de verdade, garantido pelo banco, não pela ordem
das operações.

## A pegadinha do `@Transactional` nos testes (o motivo de você ter tido esses pesadelos)

`@DataJpaTest` embrulha cada teste numa transação com rollback automático no final — ótimo
pra isolar testes, péssimo pra testar se o rollback do SEU código funciona. Como o método
`@Transactional` do serviço usa propagação `REQUIRED` (o padrão), ele só *entra* na
transação que o teste já abriu, em vez de abrir a própria. Uma falha lá dentro marca a
transação inteira como rollback-only, mas o objeto que você buscou antes continua
gerenciado em memória pelo Hibernate com o valor já alterado — um `assertEquals` nesse
objeto passa mesmo que nada tenha sido persistido de verdade. É a mistura clássica de
"gerenciado vs não gerenciado".

A correção em `CashbackApplicationServiceAtomicityTest` não é uma anotação a mais, é uma
a menos: `@Transactional(propagation = Propagation.NOT_SUPPORTED)` na classe do teste
desliga o embrulho automático do `@DataJpaTest`. Cada chamada ao serviço abre e fecha a
própria transação de verdade (igual em produção), e cada leitura de verificação depois
também abre a sua — sem cache de persistence context vazando de uma chamada pra outra.
Tem um anti-exemplo comentado no fim do arquivo de teste mostrando exatamente o
falso-positivo que essa anotação evita.

## Rodar os testes
```bash
mvn test
```
(usa H2 em memória, schema recriado a cada execução — não precisa de Docker/Kafka pra
rodar os testes de atomicidade)

## Rodar a aplicação
```bash
mvn spring-boot:run
```
Console H2 em http://localhost:8083/h2-console (JDBC URL: `jdbc:h2:mem:cashback`).

## Trocar H2 por Postgres (quando sair do modo de testes)
1. Trocar a dependência `com.h2database:h2` por `org.postgresql:postgresql` no `pom.xml`.
2. Trocar `spring.datasource.url`/`username`/`password` no `application.yml`.
3. Trocar `hibernate.ddl-auto=update` por uma migração de verdade (Flyway/Liquibase) —
   `update` é aceitável pra protótipo, não pra produção.
4. **Atenção**: o comentário em `CashbackApplicationService.applyCashback` sobre não
   capturar `DataIntegrityViolationException` dentro do método `@Transactional` importa
   ainda mais aqui — H2 é tolerante a continuar a transação depois de um erro, Postgres
   não é (aborta a transação inteira até um `ROLLBACK` explícito). O código já está
   escrito da forma portável (deixa propagar), mas se alguém "otimizar" isso depois
   adicionando um try/catch ali dentro, vai funcionar no H2 e quebrar no Postgres.

## Limitações conhecidas
- `OutboxPublisher` funciona só com Kafka rodando — sem Kafka, os eventos ficam
  acumulados como `PENDING` e o log mostra falha de publish a cada ciclo (comportamento
  esperado, não é bug).
- Sem paginação de verdade além do `findTop50` — para um outbox com volume alto, isso
  precisaria de um índice em `(status, id)` e possivelmente particionamento por data.
- Ainda não há um teste de concorrência real (duas threads/transações batendo ao mesmo
  tempo) — o teste `concurrentDuplicateLosesRaceViaUniqueConstraintNotViaPreCheck` prova
  que a constraint rejeita a duplicata, mas não simula a corrida com duas threads de
  verdade. Um próximo passo natural é um teste com `ExecutorService` disparando duas
  chamadas simultâneas e verificando que só uma delas retorna `APPLIED_NOW`.
