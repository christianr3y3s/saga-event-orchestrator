import { useState } from "react";
import { listarEstoque, buscarPorSku } from "../api/estoqueApi.js";

export function EstoqueList({ apiBase }) {
  const [itens, setItens] = useState(null);
  const [erro, setErro] = useState(null);
  const [carregando, setCarregando] = useState(false);
  const [skuBusca, setSkuBusca] = useState("");
  const [itemBuscado, setItemBuscado] = useState(undefined); // undefined = ainda não buscou

  async function carregarTudo() {
    setErro(null);
    setCarregando(true);
    try {
      setItens(await listarEstoque(apiBase));
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }

  async function buscarSku(e) {
    e.preventDefault();
    setErro(null);
    setCarregando(true);
    try {
      setItemBuscado(await buscarPorSku(apiBase, skuBusca));
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <section aria-label="Consulta de estoque">
      <h2>Estoque</h2>

      <button type="button" onClick={carregarTudo} disabled={carregando}>
        {carregando ? "Carregando..." : "Listar todos os itens"}
      </button>

      <form onSubmit={buscarSku}>
        <label>
          Buscar por SKU
          <input type="text" value={skuBusca} onChange={(e) => setSkuBusca(e.target.value)} />
        </label>
        <button type="submit" disabled={carregando || !skuBusca}>Buscar</button>
      </form>

      {erro && <p role="alert">{erro}</p>}

      {itemBuscado !== undefined && (
        itemBuscado === null
          ? <p>Nenhum item encontrado para o SKU informado.</p>
          : <p>{itemBuscado.sku}: {itemBuscado.availableQuantity} unidades disponíveis</p>
      )}

      {itens && (
        itens.length === 0 ? (
          <p>Nenhum item cadastrado.</p>
        ) : (
          <table>
            <thead><tr><th>SKU</th><th>Quantidade disponível</th></tr></thead>
            <tbody>
              {itens.map((item) => (
                <tr key={item.sku}>
                  <td>{item.sku}</td>
                  <td>{item.availableQuantity}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
    </section>
  );
}
