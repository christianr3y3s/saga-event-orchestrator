# Domínio: Logística — proposta de design (ainda não implementado)

Este arquivo é uma proposta, não uma implementação. Ao contrário de `domains/loja/`, não
tem `orchestrator.logistica.properties` nem código ainda — as perguntas abaixo têm
respostas caras de errar (custo recorrente de API, formato de dados reais, escopo do
algoritmo), então vale alinhar antes de gerar código em cima de suposições.

## Duas coisas que estão sendo faladas como uma só

"Caixeiro viajante" e "domínio de logística" não são a mesma peça arquitetural, e vale
separar antes de desenhar:

1. **Motor de otimização de rota** (TSP/VRP) — um serviço computacional: recebe pontos,
   devolve a melhor sequência/rota. Não tem estado de negócio, não precisa do orquestrador
   de saga — é uma chamada de API (dado X, calcule Y), do mesmo jeito que `CashbackCalculator`
   é uma função pura dentro do domínio cashback.
2. **Ciclo de vida da entrega** (se for isso que se quer) — Pedido de entrega criado →
   rota calculada → transportadora atribuída → despachado → entregue/falhou (com
   compensação: reatribuir a outra transportadora). Isso SIM é saga-shaped e usaria o
   mesmo motor Rust genérico, chamando o motor de otimização como um passo do fluxo.

A proposta abaixo cobre o motor de otimização (peça 1). A peça 2 só faz sentido detalhar
depois que o escopo do primeiro estiver fechado.

## Sobre o exemplo do asfalto Santos–Belém: provavelmente não é TSP

Vale examinar esse caso concreto antes de generalizar, porque ele tem características que
mudam o problema:

- **Distância**: Santos (SP) a Belém (PA) por rodovia são ~2.900–3.300 km, viagem de
  vários dias — não é uma rota curta com múltiplas paradas de entrega no mesmo dia.
- **Carga a granel com controle de temperatura**: CAP (Cimento Asfáltico de Petróleo)
  transportado a granel normalmente vai em caminhão-tanque isolado termicamente — a
  restrição real não é "em que ordem visitar pontos", é **tempo total de trânsito vs.
  perda de temperatura da carga**.
- **Restrições regulatórias de caminhão pesado no Brasil** que pesam mais que a
  sequência de paradas:
  - Lei do Motorista (13.103/2015): limite de condução contínua (~5h30) com parada
    obrigatória de descanso, limite de jornada diária.
  - PBT/PBTC (peso bruto total/combinado) por eixo — restrições de ponte/viaduto em
    parte do trajeto.
  - Postos de pesagem obrigatórios em certos pontos da rota.
  - Praças de pedágio — custo variável real por rota escolhida, não só distância.

Se o objetivo real é "qual rota devo escolher entre Santos e Belém para uma carga de
asfalto", o problema é mais próximo de **shortest path com restrições** (rota viável que
respeita peso/altura/descanso, minimizando tempo-em-trânsito ou custo) do que do
caixeiro-viajante clássico (que é sobre a ORDEM de visitar múltiplos pontos). TSP/VRP
faz sentido se essa transportadora atende MÚLTIPLAS entregas/clientes que podem ser
combinadas numa mesma viagem ou numa mesma janela — aí sim vira "em que ordem visito os
pontos de entrega" ou "como divido os pontos entre os caminhões disponíveis" (isso já é
VRP, não TSP puro).

**Pergunta em aberto**: o caso de uso real é (a) escolher a melhor rota ponto-a-ponto
para uma carga com restrições, (b) otimizar a sequência de múltiplas entregas de uma
mesma transportadora, ou (c) os dois, em domínios/momentos diferentes?

## Recomendação de escopo (default caso não haja objeção)

Dado que não há resposta fechada ainda, a recomendação é construir em camadas, cada uma
útil sozinha:

1. **Camada de distância/rota** (a base de tudo): um serviço que, dado dois pontos,
   devolve distância + tempo estimado + geometria da rota rodoviária — não caminho reto
   (great-circle), rota real de estrada.
2. **Camada de matriz de distância**: dado N pontos, devolve a matriz NxN de
   distância/tempo entre todos os pares — é o insumo que qualquer solver de
   TSP/VRP precisa.
3. **Camada de solver**: dado a matriz, resolve a sequência ótima (ou heurística) —
   plugável, para não travar a arquitetura numa única biblioteca/algoritmo.
4. **Camada de restrições** (a mais específica do caso real): peso/eixo, janela de
   tempo, descanso obrigatório do motorista — entra DEPOIS que o caso de uso estiver
   confirmado, porque é o que muda entre "loja com entrega local" e "transportadora de
   carga pesada interestadual".

## API de mapas/roteirização — comparação

| Opção | Custo | Dados de estrada p/ caminhão pesado | Hospedagem |
|---|---|---|---|
| **OSRM self-hosted** | Grátis (só infra) | Não nativamente — motor genérico de carro; dá pra customizar profile (peso/altura) com engenharia extra | Você hospeda (extrato OSM da região) |
| **OpenRouteService** | Grátis até um limite, self-hostable também | Tem perfil `driving-hgv` (heavy goods vehicle) com peso/altura/comprimento — mais adequado ao caso de caminhão pesado | Cloud gratuito com rate limit, ou self-hosted |
| **Google Maps Platform (Routes API)** | Pago por chamada, escala rápido em matriz de distância (N² chamadas) | Sem perfil dedicado de caminhão pesado na API pública | Só cloud da Google |
| **GraphHopper** | Grátis (community edition self-hosted) ou pago (cloud) | Tem perfil de caminhão com peso/altura/eixo | Self-hosted ou cloud |

**Recomendação**: para o caso de carga pesada (peso/altura importam de verdade),
**OpenRouteService** ou **GraphHopper** — ambos têm perfil de veículo pesado, o que o
OSRM genérico não tem sem trabalho extra de customização de profile. Se o volume de
chamadas for baixo (poucas rotas calculadas por dia, não uma malha de entregas urbanas
de alto volume), o tier gratuito/self-hosted de qualquer um dos dois cobre; Google Maps
só valeria a pena se precisar de dados de trânsito em tempo real, o que não parece ser o
caso de uma rota interestadual de vários dias.

## Sobre "dados reais de transportadora"

Duas perguntas que mudam a resposta técnica bastante:
- **Formato**: é uma API que a transportadora expõe, uma planilha/CSV que eles exportam,
  ou acesso a um banco deles? Cada um implica uma camada de ingestão diferente.
- **Direito de uso**: mesmo com acesso técnico aos dados, vale confirmar que há
  autorização da transportadora pra esse uso (ex.: rotas/preços podem ser informação
  comercialmente sensível).

Até essas respostas existirem, a recomendação é modelar o domínio com dados de
referência/simulados (rotas conhecidas entre capitais, pesos e restrições típicas de
transporte de carga a granel) — o desenho fica pronto pra plugar dados reais depois sem
reescrever a camada de solver.

## Próximos passos

Antes de eu escrever código, preciso de resposta objetiva em três pontos:
1. TSP (sequência de pontos) ou o problema de rota-com-restrições descrito acima pro
   caso do asfalto — ou os dois, em domínios separados?
2. OpenRouteService ou GraphHopper (ambos grátis/self-hostable com perfil de caminhão
   pesado) — alguma preferência, ou decido por você?
3. Dados de referência agora, plugar dados reais depois — confirma esse caminho?
