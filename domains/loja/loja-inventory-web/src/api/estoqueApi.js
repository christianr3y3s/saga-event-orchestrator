// Cliente HTTP puro (sem React) para loja-inventory-service. Só leitura -- não existe (e
// não deve existir) nenhuma função aqui que reserve/libere estoque por HTTP; ver
// domains/loja/README.md, seção "API HTTP (somente leitura)".

export const DEFAULT_ESTOQUE_BASE_URL = "http://localhost:8084";

async function getJson(baseUrl, path) {
  const resposta = await fetch(baseUrl + path);
  const texto = await resposta.text();
  let corpo;
  try {
    corpo = texto ? JSON.parse(texto) : null;
  } catch {
    throw new Error(`Resposta não é um JSON válido (HTTP ${resposta.status}): ${texto}`);
  }
  if (!resposta.ok) {
    if (resposta.status === 404) {
      return null; // SKU não encontrado -- não é um erro para quem chama buscarPorSku
    }
    const mensagem = (corpo && (corpo.mensagem || corpo.message)) || `HTTP ${resposta.status}`;
    throw new Error(mensagem);
  }
  return corpo;
}

export function listarEstoque(baseUrl) {
  return getJson(baseUrl, "/estoque");
}

export function buscarPorSku(baseUrl, sku) {
  return getJson(baseUrl, `/estoque/${encodeURIComponent(sku)}`);
}
