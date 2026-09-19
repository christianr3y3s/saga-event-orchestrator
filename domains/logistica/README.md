# Domínio: Logística

Ver `DESIGN.md` neste mesmo diretório para o raciocínio completo por trás das escolhas
abaixo -- em especial a separação entre "motor de otimização de rota" (implementado aqui)
e "ciclo de vida da entrega" (ainda não implementado, é saga-shaped e ficaria em cima do
motor genérico, do jeito que `loja` e `cashback` já usam).

## O que está construído: motor de otimização de rota

Um serviço síncrono e sem estado -- dado um conjunto de pontos e um caminhão, devolve a
sequência de visita, a distância, o consumo estimado e (opcionalmente) o frete. **Não** é
um participante da saga: não tem `orchestrator.logistica.properties`, não tem `topics.txt`,
não consome nem publica em Kafka. É uma chamada de API (dado X, calcule Y), do mesmo jeito
que `CashbackCalculator` é uma função pura dentro do domínio cashback -- só que aqui a
função pura está atrás de um controller REST, porque é ela o produto (é o "primeiro
produto público" mencionado nas tarefas do domínio).

```
POST /rotas/simular            POST /fretes/calcular
        │                              │
        ▼                              ▼
 SimuladorRota                  CalculadoraFrete
        │                              │
        ▼                              │
 ProvedorDistancias (matriz NxN)       │
        │                              │
        ▼                              │
 TspSolver (vizinho mais próximo       │
            + 2-opt)                   │
        │                              │
        └───────────► TipoCaminhao ◄───┘
                    (consumo por carga)
```

Camadas de `DESIGN.md` cobertas: 1 (distância), 2 (matriz) e 3 (solver). A camada 4
(restrições de peso/eixo, janela de tempo, descanso obrigatório do motorista) **não** foi
implementada -- ela só faz sentido depois que um caso de uso real a exigir (ver "Em
aberto" abaixo).

## Módulos

### `logistica-importacao-service`

Recebe planilhas Excel de entregas/custos e grava no Postgres -- essa base serve tanto
para calibrar o consumo nominal abaixo (consumo médio REAL por modelo, em vez do valor
fixo da tabela) quanto como dataset para treinamento de IA. Ver
`logistica-importacao-service/README.md` para o contrato de colunas, os endpoints e as
limitações. **Ainda não está integrado** com `logistica-rota-service` (são dois serviços
HTTP independentes hoje) -- ligar os dois é o próximo passo óbvio.

### `logistica-rota-service`

Serviço Spring Boot (Java 17, Maven), porta `8085`.

**Domínio** (`com.lab.logistica.rota.domain`, sem dependência de Spring exceto os dois
componentes marcados):
- `Coordenada` -- valida lat/lng no construtor.
- `GeoUtils` -- distância geodésica (haversine).
- `TipoCaminhao` -- a tabela de modelos validada (Rodotrem, Carreta 4 Eixos, Carreta 30 T),
  com interpolação linear de consumo entre vazio e carga máxima.
- `Consumo` -- limite de 2,5 km/L. Deliberadamente só se aplica a um consumo **medido**,
  nunca ao nominal da tabela (o nominal do rodotrem carregado é 2,15 km/L -- aplicar o
  corte a ele dispararia sempre, o que foi um bug do protótipo original).
- `ProvedorDistancias` (interface) + `HaversineProvedorDistancias` (`@Component`) --
  implementação de referência em linha reta × fator de sinuosidade configurável
  (`app.rota.fator-rodoviario`, default 1.3). **Não é rota rodoviária real** -- ver "Em
  aberto".
- `TspSolver` -- vizinho mais próximo + 2-opt, plugável.
- `SimuladorRota` (`@Component`) -- orquestra as três camadas acima.
- `ParametrosFrete` / `ResultadoFrete` / `CalculadoraFrete` (`@Component`) -- frete =
  combustível + operacional + pedágios + margem (markup sobre o custo, não sobre o preço
  de venda). Valores monetários em `BigDecimal`, arredondados a 2 casas (`HALF_UP`) em
  cada linha. Sem impostos.

**API** (`com.lab.logistica.rota.api`):
- `POST /rotas/simular` -- `{ caminhao, cargaToneladas, pontos: [{lat,lng}, ...] }` →
  ordem de visita (circuito fechado), distância, consumo, litros.
- `POST /fretes/calcular` -- `{ caminhao, cargaToneladas, distanciaKm, precoDieselPorLitro,
  pedagios, custoOperacionalPorKm, margemPercentual, pisoMinimo? }` → composição do frete.
- Entrada inválida (carga acima da capacidade, distância ≤ 0, coordenada fora de faixa,
  etc.) responde `400` com `{ "mensagem": "..." }` em vez de vazar erro 500.

## Testes

- **Unitários** (`src/test/.../domain`): cobrem `Coordenada`, `GeoUtils`, `TipoCaminhao`
  (inclusive a conferência com a tabela validada), `Consumo`, `HaversineProvedorDistancias`,
  `TspSolver` e `SimuladorRota`. `CalculadoraFreteTest` roda os mesmos três casos de
  aceitação (A: carreta 4 eixos carregada, B: carreta 30 t vazia, C: rodotrem carregado)
  já usados na página web de frete e no protótipo Android -- os três lados (Kotlin, HTML/JS
  e este serviço Java) precisam concordar; se mudar uma fórmula aqui, mude nos outros dois
  também.
- **Integração** (`src/test/.../api/RotaControllerIntegrationTest`, `@Tag("integration")`):
  sobe o contexto Spring inteiro num servidor HTTP real (porta aleatória) e chama os dois
  endpoints via `TestRestTemplate`, ponta a ponta, sem mocks -- no mesmo espírito das
  `*AtomicityTest` de `cashback`/`loja` (lá é "Spring context + H2 de verdade"; aqui, por
  não ter persistência, é "Spring context + HTTP de verdade"). Cobre os três casos de
  aceitação via HTTP, o aviso de piso mínimo, os erros 400 (carga acima da capacidade,
  distância zero, coordenada inválida) e uma simulação de rota com 3 pontos.

### Rodando localmente

```bash
cd domains/logistica/logistica-rota-service
mvn test                              # unitários + integração
mvn spring-boot:run                   # sobe em :8085
```

Teste ponta a ponta manual (o "teste reproduzível" pedido em `docs/adding-a-domain.md`):

```bash
curl -s localhost:8085/fretes/calcular -H 'Content-Type: application/json' -d '{
  "caminhao": "CARRETA_4_EIXOS", "cargaToneladas": 38, "distanciaKm": 500,
  "precoDieselPorLitro": 6.00, "pedagios": 120.00,
  "custoOperacionalPorKm": 2.50, "margemPercentual": 15
}'
# esperado: "frete": 2877.39
```

> **Build não verificado neste ambiente**: o acesso ao Maven Central (`repo.maven.apache.org`)
> está bloqueado pela política de rede desta sessão (proxy retornou 403), então `mvn test`
> não pôde ser executado aqui. A lógica de domínio (tabela de consumo, haversine, TSP,
> cálculo de frete com os três casos de aceitação) foi conferida à parte com `javac`/`java`
> puro, sem Spring nem JUnit -- todos os valores bateram. A camada Spring (controller,
> `@Component`, testes de integração) não foi compilada nem executada. Rode `mvn test` no
> seu ambiente antes de considerar isto pronto para produção.

## Em aberto (não decidido nesta rodada)

As três perguntas de `DESIGN.md` sobre a peça 2 (ciclo de vida da entrega) e sobre dados
reais continuam sem resposta -- o que foi construído agora é só a peça 1 (motor de
otimização), com dados de referência/simulados, exatamente a opção "default caso não haja
objeção" que o `DESIGN.md` já apontava. Antes de ir para produção com carga real:

1. **API de roteirização real.** `HaversineProvedorDistancias` é linha reta × fator fixo --
   não sabe de restrição de peso/altura, posto de pesagem ou praça de pedágio. Trocar por
   uma implementação de `ProvedorDistancias` sobre OpenRouteService (perfil `driving-hgv`)
   ou GraphHopper (perfil de caminhão) antes de usar em decisão real de rota de carga
   pesada -- a interface já isola essa troca do resto do código.
2. **TSP vs. rota-com-restrições.** Isto resolve "em que ordem visitar N pontos". Para o
   caso do asfalto Santos-Belém (ou qualquer rota longa com restrição de peso/jornada), o
   problema real é outro (shortest path com restrições) -- ver a seção correspondente em
   `DESIGN.md`.
3. **Ciclo de vida da entrega**, se for necessário: pedido → rota calculada → transportadora
   atribuída → despachado → entregue/falhou, com compensação. Isso sim é saga-shaped e
   seguiria `docs/adding-a-domain.md` normalmente (properties, FSM, tópicos), chamando este
   serviço como um passo do fluxo.
