# Domínio: Logística

> **Decisão de produto (2026-09-19):** `logistica-rota-service` v1.0.0 é o case de
> apresentação ao mercado de cargas -- rota + frete, hospedado e com interface, é o que se
> mostra primeiro. NF-e/SEFAZ (`domains/nfe`) e qualquer integração contábil ficam como
> "extra": construídos até onde já estão (resolução de endpoint SEFAZ), mas fora do caminho
> crítico até o motor de rota/frete provar valor com empresas reais. Ver "Em aberto" abaixo
> para o que falta antes de tratar os números de rota como confiáveis puros.

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
- `ProvedorDistancias` (interface) + duas implementações, escolhidas por
  `app.rota.distancia-provider` sem mudar código (DIP):
  - `HaversineProvedorDistancias` (default) -- linha reta × fator de sinuosidade
    configurável (`app.rota.fator-rodoviario`, default 1.3). Rápida, sem chave, **não é
    rota rodoviária real**.
  - `OpenRouteServiceProvedorDistancias` (`distancia-provider=openrouteservice`) --
    distância rodoviária real via Matrix API da OpenRouteService, perfil `driving-hgv`
    (caminhão pesado). Exige `app.rota.ors.api-key` (grátis em
    openrouteservice.org/dev/#/signup). **É a que deve estar ativa antes de qualquer
    demonstração para o mercado** -- ver "Em aberto" sobre o que ainda falta mesmo com
    ela ligada.
- `SolverRota` (interface) + `VizinhoMaisProximoComDoisOpt` (`@Component`) -- vizinho mais
  próximo + 2-opt. Antes desta rodada de revisão, o solver era uma classe estática
  (`TspSolver`) chamada diretamente por `SimuladorRota`: a Javadoc dizia "plugável", mas
  trocar de algoritmo exigia editar `SimuladorRota`. Extrair a interface fecha essa lacuna
  entre DIP e a implementação -- agora é o mesmo padrão de injeção já usado em
  `ProvedorDistancias`.
- `SimuladorRota` (`@Component`) -- orquestra as três camadas acima.
- `ParametrosFrete` / `ResultadoFrete` / `CalculadoraFrete` (`@Component`) -- frete =
  combustível + operacional + pedágios + margem (markup sobre o custo, não sobre o preço
  de venda). Valores monetários em `BigDecimal`, arredondados a 2 casas (`HALF_UP`) em
  cada linha. Sem impostos.

**API** (`com.lab.logistica.rota.api`):
- `RotaController` -- `POST /rotas/simular` -- `{ caminhao, cargaToneladas,
  pontos: [{lat,lng}, ...] }` → ordem de visita (circuito fechado), distância, consumo,
  litros.
- `FreteController` -- `POST /fretes/calcular` -- `{ caminhao, cargaToneladas, distanciaKm,
  precoDieselPorLitro, pedagios, custoOperacionalPorKm, margemPercentual, pisoMinimo? }` →
  composição do frete. Separado de `RotaController` por SRP (revisão desta rodada) -- um
  único controller respondia pelos dois endpoints antes, misturando duas razões de mudança.
- Entrada inválida (carga acima da capacidade, distância ≤ 0, coordenada fora de faixa,
  etc.) responde `400` com `{ "mensagem": "..." }` em vez de vazar erro 500.

## Testes

- **Unitários** (`src/test/.../domain`): cobrem `Coordenada`, `GeoUtils`, `TipoCaminhao`
  (inclusive a conferência com a tabela validada), `Consumo`, `HaversineProvedorDistancias`,
  `VizinhoMaisProximoComDoisOpt` e `SimuladorRota` (inclusive um teste com um `SolverRota`
  falso, provando que trocar de algoritmo é só trocar o que é passado no construtor, sem
  mexer em `SimuladorRota` -- a prova concreta da correção DIP desta rodada).
  `OpenRouteServiceProvedorDistanciasTest` cobre só a lógica pura (montar o corpo da
  requisição com longitude/latitude invertidas, interpretar a resposta, rejeitar célula
  nula "sem rota" em vez de silenciar como zero) -- sem rede, sem mockar `RestTemplate`.
  `CalculadoraFreteTest` roda os mesmos três casos de
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
> cálculo de frete com os três casos de aceitação, e a montagem/interpretação de
> requisição-resposta da OpenRouteService) foi conferida à parte com `javac`/`java` puro --
> a parte da ORS pôde ser testada contra o Jackson de verdade (jars já presentes nesta
> máquina via Gradle), não só simulada; todos os valores bateram. A camada Spring
> (controller, `@Component`, a chamada HTTP em si de `OpenRouteServiceProvedorDistancias`,
> testes de integração) não foi compilada nem executada. Rode `mvn test` no seu ambiente, e
> faça pelo menos uma chamada real com `distancia-provider=openrouteservice` contra uma
> rota conhecida, antes de considerar isto pronto para uma demonstração.

## Revisão de fronteiras (Clean Code / SOLID) desta rodada

Auditoria mecânica (grep de dependências entre pacotes + verificação de tamanho de classe)
sobre os três módulos deste domínio mais `nfe/sefaz-integration`, focada em
`logistica-rota-service` por ser o "primeiro serviço público" (prioridade explícita desta
rodada). Achados e correções:

1. **DIP em `TspSolver` (corrigido)** -- a Javadoc dizia "plugável", mas o solver era só
   métodos estáticos chamados diretamente por `SimuladorRota`; trocar de algoritmo exigia
   editar `SimuladorRota`. Extraída a interface `SolverRota`, com `VizinhoMaisProximoComDoisOpt`
   como implementação de referência, injetada por construtor do mesmo jeito que
   `ProvedorDistancias` já era. `TspSolver` foi removido.
2. **SRP em `RotaController` (corrigido)** -- um único controller respondia por
   `/rotas/simular` e `/fretes/calcular`, duas responsabilidades sem relação direta. Split
   em `RotaController` (só rotas) e `FreteController` (só frete).
3. **Duplicação em `ParametrosFrete` (corrigido)** -- quatro blocos quase idênticos de
   validação "não pode ser negativo" no construtor compacto, extraídos para um método
   privado `exigirNaoNegativo`.
4. **Dependency Rule invertida em `logistica-importacao-service` (corrigido)** -- ver
   `logistica-importacao-service/README.md`, seção "Revisão de fronteiras": o domínio
   importava e devolvia tipos de `api.dto` diretamente.
5. **`nfe/sefaz-integration` (sem violações)** -- biblioteca pequena e coesa, sem
   dependência de framework; nada a corrigir.
6. **`@Entity` no pacote `dominio` (aceito, não é violação nesta convenção)** --
   `EntregaHistorico` mistura anotação JPA com lógica de domínio. Isto é academicamente
   discutível, mas é a mesma convenção já usada em `cashback-outbox-jpa` e
   `loja-inventory-service` -- mudar só aqui criaria inconsistência com o resto do
   repositório sem que ninguém tivesse pedido isso.

## Em aberto (não decidido nesta rodada)

As três perguntas de `DESIGN.md` sobre a peça 2 (ciclo de vida da entrega) e sobre dados
reais continuam sem resposta -- o que foi construído agora é só a peça 1 (motor de
otimização), com dados de referência/simulados, exatamente a opção "default caso não haja
objeção" que o `DESIGN.md` já apontava. Antes de ir para produção com carga real:

1. **API de roteirização real (implementada, precisa ser ativada e testada).**
   `OpenRouteServiceProvedorDistancias` já existe -- falta: (a) gerar a chave grátis em
   openrouteservice.org/dev/#/signup, (b) configurar `APP_ROTA_DISTANCIA_PROVIDER=openrouteservice`
   e `APP_ROTA_ORS_API_KEY=<chave>` no host (variáveis de ambiente na Railway, por exemplo
   -- não editar `application.yml` com a chave real), e (c) testar contra uma rota conhecida
   (ex.: Santos-Belém) antes de qualquer demonstração, porque a chamada HTTP em si não foi
   exercitada nesta sessão (ver ressalva de build acima). O plano B (GraphHopper) continua
   disponível se a ORS não servir -- é só escrever outra implementação de `ProvedorDistancias`
   e trocar a mesma variável de ambiente, sem editar `SimuladorRota`.
2. **TSP vs. rota-com-restrições.** Isto resolve "em que ordem visitar N pontos". Para o
   caso do asfalto Santos-Belém (ou qualquer rota longa com restrição de peso/jornada), o
   problema real é outro (shortest path com restrições) -- ver a seção correspondente em
   `DESIGN.md`.
3. **Ciclo de vida da entrega**, se for necessário: pedido → rota calculada → transportadora
   atribuída → despachado → entregue/falhou, com compensação. Isso sim é saga-shaped e
   seguiria `docs/adding-a-domain.md` normalmente (properties, FSM, tópicos), chamando este
   serviço como um passo do fluxo.
