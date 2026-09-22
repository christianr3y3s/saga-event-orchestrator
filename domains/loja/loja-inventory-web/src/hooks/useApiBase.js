import { useState } from "react";
import { DEFAULT_ESTOQUE_BASE_URL } from "../api/estoqueApi.js";

const CHAVE = "loja-inventory-web:estoque-base-url";

export function useEstoqueApiBase() {
  const [baseUrl, setBaseUrlState] = useState(() => {
    try {
      return window.localStorage.getItem(CHAVE) || DEFAULT_ESTOQUE_BASE_URL;
    } catch {
      return DEFAULT_ESTOQUE_BASE_URL;
    }
  });
  const setBaseUrl = (novoValor) => {
    setBaseUrlState(novoValor);
    try {
      window.localStorage.setItem(CHAVE, novoValor);
    } catch {
      // localStorage indisponível -- segue só em memória
    }
  };
  return [baseUrl, setBaseUrl];
}
