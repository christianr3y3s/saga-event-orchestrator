# logistica-rota-web

Front-end React para o domínio `logistica`: três telas, consumindo os dois back-ends REST
já existentes (nenhum endpoint novo foi criado para este front-end).

| Tela | Endpoint | Serviço | Porta padrão |
|---|---|---|---|
| Simular rota | `POST /rotas/simular` | `logistica-rota-service` | 8085 |
| Calcular frete | `POST /fretes/calcular` | `logistica-rota-service` | 8085 |
| Histórico de entregas | `GET /historico`, `GET /historico/consumo-medio` | `logistica-importacao-service` | 8086 |

As duas URLs base são configuráveis na tela (seção "Configuração da API", persistida em
`localStorage`) — mesmo padrão já usado em `frete-aceitacao.html`.

## Estrutura

```
src/
  api/logisticaApi.js       # cliente HTTP puro (sem React) -- fetch, sem dependências
  hooks/useApiBase.js        # leitura/escrita das URLs base em localStorage
  components/
    ApiConfig.jsx
    SimularRota.jsx
    CalcularFrete.jsx
    Historico.jsx
  App.jsx                     # navegação entre as 3 telas
  main.jsx                     # ponto de entrada (ReactDOM.createRoot)
spec/
  api/logisticaApi.spec.js         # Jasmine, sem DOM
  components/*.spec.jsx            # Jasmine + @testing-library/react + jsdom
  support/jasmine.json
  helpers/babel-register.cjs       # transpila JSX/ESM em tempo de require() para o Jasmine
  helpers/jsdom-setup.js           # injeta window/document globais (jsdom-global)
```

## Rodar

```bash
cd domains/logistica/logistica-rota-web
npm install
npm run dev        # http://localhost:5173, com os back-ends rodando em 8085/8086
npm test           # suíte Jasmine
npm run build       # build de produção (Vite) -- gera dist/, hospedável como estático
```

## Limitações do ambiente onde este código foi escrito (leia antes de desconfiar dos testes)

Diferente do resto do monorepo (onde o bloqueio conhecido era só o Maven Central), aqui
**o próprio `npm install` está bloqueado no sandbox onde este código foi gerado** —
confirmado tanto contra o registro (`registry.npmjs.org`, HTTP 403 direto e via proxy)
quanto contra os três CDNs mais comuns (`cdnjs.cloudflare.com`, `cdn.jsdelivr.net`,
`unpkg.com`, todos com `CONNECT` recusado pelo proxy). Ou seja: **nenhuma dependência deste
`package.json` (React incluso) pôde ser baixada e o Jasmine nunca rodou de verdade aqui.**

O que foi possível verificar mesmo assim, e como:

- **`src/api/logisticaApi.js`** (o cliente HTTP, sem React) — testado de verdade com Node
  22 (`fetch` nativo, sem nenhuma dependência externa), mockando `global.fetch` com os
  mesmos casos que `spec/api/logisticaApi.spec.js` cobre em Jasmine. Todos os casos
  passaram (POST correto, tratamento de erro HTTP não-2xx, GET com query string, erro claro
  quando a resposta não é JSON). O `.spec.js` é a versão formal Jasmine da mesma cobertura.
- **`parsePontos` (lógica de `SimularRota.jsx`)** — extraída e testada isoladamente com Node
  puro (parsing de "lat,lng" por linha, erro em linha malformada, lista vazia).
- **Composição dos componentes (hooks + JSX)** — este ambiente tem, por acaso, uma cópia do
  React 19.2.6 real instalada globalmente (dependência de outra ferramenta, não baixada por
  mim). Rodei uma reprodução em `React.createElement` puro (sem JSX/Babel, que também não
  pôde ser baixado) da árvore de `<App/>` com `react-dom/server`, e o render funcionou sem
  lançar exceção. Isso confirma que o *padrão* de composição (hooks, várias telas
  condicionais, props) é sólido, mas **não substitui rodar os arquivos `.jsx` reais nem os
  testes de interação (`fireEvent`, `waitFor`) de `spec/components/*.spec.jsx`** -- esses só
  vão rodar de verdade na primeira máquina com acesso ao npm.

**Ação necessária antes de considerar isto pronto:** rode `npm install && npm test` numa
máquina com acesso normal à internet. Se algo no encadeamento Babel/Jasmine/jsdom não
funcionar de primeira (é uma combinação incomum -- Jest seria o caminho mais testado do
ecossistema), o candidato mais provável é `spec/support/jasmine.json` /
`spec/helpers/babel-register.cjs`, não a lógica dos componentes em si.
