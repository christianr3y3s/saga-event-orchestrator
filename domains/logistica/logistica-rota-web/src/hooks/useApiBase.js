import { useState } from "react";
import { DEFAULT_ROTA_BASE_URL, DEFAULT_HISTORICO_BASE_URL } from "../api/logisticaApi.js";

// Mesmo padrão de persistência já usado em frete-aceitacao.html: guarda a URL do back-end
// em localStorage para não precisar redigitar a cada visita. Extraído como hook próprio
// (SRP) para poder testar a lógica de leitura/escrita sem renderizar nenhum componente.

const CHAVE_ROTA = "logistica-rota-web:rota-base-url";
const CHAVE_HISTORICO = "logistica-rota-web:historico-base-url";

function lerOuDefault(chave, valorDefault) {
  try {
    return window.localStorage.getItem(chave) || valorDefault;
  } catch {
    return valorDefault;
  }
}

export function useRotaApiBase() {
  const [baseUrl, setBaseUrlState] = useState(() => lerOuDefault(CHAVE_ROTA, DEFAULT_ROTA_BASE_URL));
  const setBaseUrl = (novoValor) => {
    setBaseUrlState(novoValor);
    try {
      window.localStorage.setItem(CHAVE_ROTA, novoValor);
    } catch {
      // localStorage indisponível (ex.: modo privado) -- segue só em memória
    }
  };
  return [baseUrl, setBaseUrl];
}

export function useHistoricoApiBase() {
  const [baseUrl, setBaseUrlState] = useState(() => lerOuDefault(CHAVE_HISTORICO, DEFAULT_HISTORICO_BASE_URL));
  const setBaseUrl = (novoValor) => {
    setBaseUrlState(novoValor);
    try {
      window.localStorage.setItem(CHAVE_HISTORICO, novoValor);
    } catch {
      // idem
    }
  };
  return [baseUrl, setBaseUrl];
}
