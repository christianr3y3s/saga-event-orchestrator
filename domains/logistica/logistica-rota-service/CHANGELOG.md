# Changelog -- `logistica-rota-service`

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). Este é o
primeiro serviço público do domínio logística (ver `domains/logistica/README.md`), por
isso a versão de estreia é `1.0.0` em vez de `0.x` -- os outros módulos do domínio
(`logistica-importacao-service`, `nfe/sefaz-integration`) continuam em `0.1.0` por ainda
não serem expostos publicamente.

## [1.0.1] -- 2026-09-19

Endurecimento para produção (revisão + cobertura + prontidão das integrações). Sem mudança
de contrato dos endpoints existentes, exceto os novos códigos de erro abaixo.

### Adicionado
- **Gate de cobertura**: `mvn verify` falha abaixo de 95% de linhas e de branches (JaCoCo).
  Estado atual: 100% / 100% (92 testes; só a classe com `main()` fica fora da conta).
- `GET /actuator/health` (com probes de liveness/readiness) para o orquestrador de deploy;
  somente `health` e `info` são expostos.
- Teto de pontos por `/rotas/simular` (`app.rota.max-pontos`, default 50 = limite da Matrix
  API grátis da ORS) -- sem ele um request grande esgotava CPU/memória (matriz NxN + 2-opt).
- Timeouts de conexão (3 s) e leitura (10 s) na chamada à ORS
  (`app.rota.ors.connect-timeout-ms` / `read-timeout-ms`).
- Testes da chamada HTTP à ORS (URL, cabeçalho de autenticação, corpo, 4xx/5xx, resposta
  malformada) e da troca de provedor por configuração no contexto Spring.

### Corrigido
- Falha da ORS (fora do ar, 401, resposta sem rota) virava **500 genérico**; agora é **502**
  com mensagem que não vaza URL/corpo de resposta.
- `caminhao` ou `pontos` ausentes (ou com item nulo) em `/rotas/simular` viravam NPE/500;
  agora são **400** com a mensagem do campo.
- ORS sem `app.rota.ors.api-key` continua falhando na **inicialização** (não no 1º request) --
  agora coberto por teste.

### Verificação
- Build e testes agora rodam de verdade com Maven (o aviso de "não verificado" da 1.0.0
  deixa de valer).
- **Ainda não verificado**: chamada contra a OpenRouteService REAL (exige chave; ver README,
  "Homologação da ORS"). Sem autenticação nos endpoints e CORS default `*` continuam como na
  1.0.0 -- ver "Antes de expor publicamente".

## [1.0.0] -- 2026-09-19

### Adicionado
- Motor de otimização de rota (`POST /rotas/simular`): distância, matriz de distâncias
  (haversine × fator de sinuosidade configurável) e solver de vizinho mais próximo + 2-opt.
- Cálculo de frete (`POST /fretes/calcular`): combustível + operacional + pedágios + margem,
  com os três casos de aceitação (A/B/C) usados também na página web e no protótipo Android.
- Tabela de modelos de caminhão validada (Rodotrem, Carreta 4 Eixos, Carreta 30T) com
  interpolação linear de consumo entre vazio e carga máxima.
- Testes unitários de domínio e testes de integração ponta a ponta (`@Tag("integration")`,
  Spring context + HTTP real).

### Corrigido (revisão Clean Code/SOLID antes desta tag)
- **DIP**: solver de rota (antes `TspSolver`, estático) extraído para a interface
  `SolverRota`, implementada por `VizinhoMaisProximoComDoisOpt` e injetada por construtor em
  `SimuladorRota` -- trocar de algoritmo agora é trocar o bean, não editar código.
- **SRP**: `RotaController` (só `/rotas/simular`) separado de `FreteController` (só
  `/fretes/calcular`); antes um único controller respondia pelos dois.
- Duplicação de validação em `ParametrosFrete` consolidada num único método auxiliar.

### Conhecido / fora de escopo desta versão
- `HaversineProvedorDistancias` é linha reta × fator fixo, não uma rota rodoviária real --
  ver "Em aberto" em `domains/logistica/README.md` antes de decisão real de rota de carga
  pesada.
- Sem autenticação/autorização.
- Build não verificado com Maven neste ambiente (Maven Central bloqueado pela política de
  rede da sessão que gerou este código) -- lógica de domínio conferida à parte com
  `javac`/`java` puro; rode `mvn test` antes de considerar isto pronto para produção.
