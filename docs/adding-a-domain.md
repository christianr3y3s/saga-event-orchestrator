# Como adicionar um domínio novo

O motor (`engine/saga-orchestrator-rust`) não muda para isso. Um domínio novo é:
um diretório em `domains/<nome>/`, um arquivo `.properties`, e os serviços que
implementam os consumers dos eventos declarados.

## Passo a passo

1. **Crie `domains/<nome>/orchestrator.<nome>.properties`** com:
   - `event.in.<Evento>.topic=...` para cada evento que o motor vai consumir
   - `command.out.<Comando>.topic=...` para cada comando que o motor vai publicar
   - `route.<Evento>.emit=<Comando1>,<Comando2>` para o roteamento
   - **Não repita** `kafka.bootstrap.servers`, `kafka.group.id`, nem nenhuma convenção
     global — isso já está em `engine/saga-orchestrator-rust/base.properties` e é
     combinado em tempo de execução (veja `scripts/run-domain.sh`).

2. **Declare a FSM do domínio** no mesmo arquivo (`saga.<Tipo>.start/.end/.states/.state.*`)
   — mesmo que pareça óbvio no começo. É de graça (o validador já roda no bootstrap do
   motor) e é o que evita descobrir um estado sem saída em produção em vez de no `cargo run`.

3. **Rode o validador antes de implementar qualquer serviço**:
   ```bash
   ./scripts/run-domain.sh <nome>
   ```
   Se a FSM tiver um trap loop ou dead end, o motor recusa subir e te diz exatamente
   qual estado está preso. Corrija a FSM primeiro — é mais barato descobrir um buraco no
   fluxo antes de escrever código do que depois.

4. **Adicione `domains/<nome>/topics.txt`** (um tópico por linha) e crie os tópicos:
   ```bash
   ./scripts/create-topics.sh domains/<nome>/topics.txt
   ```

5. **Implemente os serviços de domínio** (Java, Kotlin, o que fizer sentido) como
   consumers/producers Kafka simples, cada um cuidando só do evento que consome e do que
   publica. Nenhum deles deve conhecer o motor além do contrato de evento (JSON com
   `type`, `correlationId`, e o resto do payload).

6. **Escreva `domains/<nome>/README.md`** com o diagrama do fluxo (copie o formato dos
   READMEs de `domains/cashback/*` como referência) e um teste ponta a ponta reproduzível.

## Coisas que NÃO precisam de código novo no motor
- Um domínio a mais rodando (basta outro `.properties`)
- Mudar rotas, eventos ou comandos de um domínio existente
- Adicionar estados/transições numa FSM existente

## Coisas que PRECISAM de mudança no motor (raras, pensar duas vezes antes)
- Um novo mecanismo de extração de correlação/tipo de evento além de payload/topic
- Mudar a semântica de commit/DLQ/retry
- Qualquer coisa que faça o motor "saber" o nome de um domínio específico -- se isso
  parecer necessário, é sinal de que a lógica deveria estar no serviço de domínio, não
  no motor.

## Sobre o domínio NF-e (planejado, ainda não implementado)
O roadmap original (`docs/roadmap/roadmap_rediscache_event_driven_nfe.pdf`) mapeia as
fases pra esse domínio: Event Store → Saga (já é o motor genérico daqui) → Read Model →
Cache Projection (Redis) → Cliente Quarkus → Resilience. A peça verdadeiramente nova em
relação ao cashback é a integração com a SEFAZ — recomenda-se isolá-la como um serviço
próprio (`domains/nfe/sefaz-integration`) que só fala com a API da SEFAZ e traduz para os
eventos do domínio (ex.: consome `XmlValidado`, publica `NfeAutorizada`/`NfeRejeitada`),
pelo mesmo motivo que `cashback-service` fica isolado do resto: a instabilidade de uma
dependência externa não deve vazar pro motor nem pros outros serviços do domínio.
