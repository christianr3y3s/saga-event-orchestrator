// Cliente HTTP puro (sem React) para os dois back-ends do domínio logistica:
//   - logistica-rota-service      (default http://localhost:8085): /rotas/simular, /fretes/calcular
//   - logistica-importacao-service (default http://localhost:8086): /historico, /historico/consumo-medio
// Mantido separado dos componentes de propósito: é a parte testável sem precisar renderizar
// nada (mesmo espírito de "SRP" já seguido nos back-ends deste monorepo).

export const DEFAULT_ROTA_BASE_URL = "http://localhost:8085";
export const DEFAULT_HISTORICO_BASE_URL = "http://localhost:8086";

async function postJson(baseUrl, path, body) {
  const resposta = await fetch(baseUrl + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  return lerRespostaJson(resposta);
}

async function getJson(baseUrl, path) {
  const resposta = await fetch(baseUrl + path);
  return lerRespostaJson(resposta);
}

async function lerRespostaJson(resposta) {
  const texto = await resposta.text();
  let corpo;
  try {
    corpo = texto ? JSON.parse(texto) : null;
  } catch {
    throw new Error(`Resposta não é um JSON válido (HTTP ${resposta.status}): ${texto}`);
  }
  if (!resposta.ok) {
    const mensagem = (corpo && (corpo.mensagem || corpo.message)) || `HTTP ${resposta.status}`;
    throw new Error(mensagem);
  }
  return corpo;
}

/**
 * @param {string} baseUrl
 * @param {{caminhao: string, cargaToneladas: number, pontos: {lat:number, lng:number}[]}} params
 */
export function simularRota(baseUrl, params) {
  return postJson(baseUrl, "/rotas/simular", params);
}

/**
 * @param {string} baseUrl
 * @param {{caminhao:string, cargaToneladas:number, distanciaKm:number, precoDieselPorLitro:number,
 *          pedagios:number, custoOperacionalPorKm:number, margemPercentual:number, pisoMinimo:number}} params
 */
export function calcularFrete(baseUrl, params) {
  return postJson(baseUrl, "/fretes/calcular", params);
}

export function listarHistorico(baseUrl) {
  return getJson(baseUrl, "/historico");
}

export function consumoMedio(baseUrl, { caminhao, cargaToneladas }) {
  const query = new URLSearchParams({ caminhao, cargaToneladas: String(cargaToneladas) });
  return getJson(baseUrl, `/historico/consumo-medio?${query}`);
}
