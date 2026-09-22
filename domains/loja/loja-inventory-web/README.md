# loja-inventory-web

Front-end React (somente leitura) para `loja-inventory-service`: uma tela de consulta de
estoque, consumindo `GET /estoque` e `GET /estoque/{sku}` (ver
`domains/loja/README.md`, seção "API HTTP (somente leitura)").

Não existe, de propósito, nenhuma tela ou chamada de reserva/liberação de estoque — isso
continua exclusivo do fluxo Kafka da saga.

## Estrutura

```
src/
  api/estoqueApi.js          # cliente HTTP puro (sem React)
  hooks/useApiBase.js         # URL base em localStorage
  components/ApiConfig.jsx
  components/EstoqueList.jsx  # única tela: listar tudo + buscar por SKU
  App.jsx
  main.jsx
spec/
  api/estoqueApi.spec.js
  components/EstoqueList.spec.jsx
  support/jasmine.json
  helpers/babel-register.cjs
  helpers/jsdom-setup.js
```

## Rodar

```bash
cd domains/loja/loja-inventory-web
npm install
npm run dev        # http://localhost:5174, com loja-inventory-service em 8084
npm test
npm run build
```

## Limitações do ambiente onde este código foi escrito

Mesma limitação documentada em detalhe em `domains/logistica/logistica-rota-web/README.md`:
neste sandbox, `npm install` (registro e os três CDNs mais comuns) está bloqueado por
política de rede, então nada aqui rodou com as dependências reais. O que foi verificado:

- `src/api/estoqueApi.js` — testado com Node 22 (`fetch` nativo, mockado manualmente),
  cobrindo os mesmos casos do `.spec.js`: listar, buscar por SKU existente, 404 vira `null`
  (não exceção), outros erros HTTP lançam com a mensagem do back-end.
- Composição dos componentes — mesma checagem de fumaça com o React 19.2.6 real (instalado
  neste ambiente por outra ferramenta) via `react-dom/server`, sem JSX/Babel (indisponíveis
  aqui). Ver o README do `logistica-rota-web` para o racional completo.

Rode `npm install && npm test` numa máquina com acesso normal ao npm antes de considerar
isto pronto.
