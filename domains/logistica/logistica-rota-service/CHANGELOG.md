# Changelog -- `logistica-rota-service`

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). Este é o
primeiro serviço público do domínio logística (ver `domains/logistica/README.md`), por
isso a versão de estreia é `1.0.0` em vez de `0.x` -- os outros módulos do domínio
(`logistica-importacao-service`, `nfe/sefaz-integration`) continuam em `0.1.0` por ainda
não serem expostos publicamente.

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
