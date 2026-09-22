import { useState } from "react";
import { listarHistorico, consumoMedio } from "../api/logisticaApi.js";

const CAMINHOES = ["RODOTREM", "CARRETA_4_EIXOS", "CARRETA_30T"];

export function Historico({ apiBase }) {
  const [entregas, setEntregas] = useState(null);
  const [erroLista, setErroLista] = useState(null);
  const [carregandoLista, setCarregandoLista] = useState(false);

  const [caminhao, setCaminhao] = useState(CAMINHOES[0]);
  const [cargaToneladas, setCargaToneladas] = useState("0");
  const [consumo, setConsumo] = useState(null);
  const [erroConsumo, setErroConsumo] = useState(null);
  const [carregandoConsumo, setCarregandoConsumo] = useState(false);

  async function carregarHistorico() {
    setErroLista(null);
    setCarregandoLista(true);
    try {
      setEntregas(await listarHistorico(apiBase));
    } catch (e) {
      setErroLista(e.message);
    } finally {
      setCarregandoLista(false);
    }
  }

  async function consultarConsumoMedio(e) {
    e.preventDefault();
    setErroConsumo(null);
    setConsumo(null);
    setCarregandoConsumo(true);
    try {
      setConsumo(await consumoMedio(apiBase, { caminhao, cargaToneladas: Number(cargaToneladas) }));
    } catch (e2) {
      setErroConsumo(e2.message);
    } finally {
      setCarregandoConsumo(false);
    }
  }

  return (
    <section aria-label="Histórico de entregas">
      <h2>Histórico de entregas</h2>

      <div>
        <button type="button" onClick={carregarHistorico} disabled={carregandoLista}>
          {carregandoLista ? "Carregando..." : "Carregar histórico"}
        </button>
        {erroLista && <p role="alert">{erroLista}</p>}
        {entregas && (
          entregas.length === 0 ? (
            <p>Nenhuma entrega importada ainda.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Data</th><th>Caminhão</th><th>Origem</th><th>Destino</th>
                  <th>Distância (km)</th><th>Consumo (km/L)</th><th>Frete cobrado</th>
                </tr>
              </thead>
              <tbody>
                {entregas.map((e) => (
                  <tr key={e.id}>
                    <td>{e.dataEntrega}</td>
                    <td>{e.caminhao}</td>
                    <td>{e.origem}</td>
                    <td>{e.destino}</td>
                    <td>{e.distanciaKm}</td>
                    <td>{e.consumoKmL ?? "—"}</td>
                    <td>R$ {e.freteCobrado}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        )}
      </div>

      <form onSubmit={consultarConsumoMedio}>
        <h3>Consumo médio calibrado</h3>
        <label>
          Caminhão
          <select value={caminhao} onChange={(e) => setCaminhao(e.target.value)}>
            {CAMINHOES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </label>
        <label>
          Carga (toneladas)
          <input
            type="number"
            step="0.1"
            min="0"
            value={cargaToneladas}
            onChange={(e) => setCargaToneladas(e.target.value)}
          />
        </label>
        <button type="submit" disabled={carregandoConsumo}>
          {carregandoConsumo ? "Consultando..." : "Consultar"}
        </button>
      </form>
      {erroConsumo && <p role="alert">{erroConsumo}</p>}
      {consumo && (
        <dl>
          <dt>Consumo médio</dt>
          <dd>{consumo.consumoKmL.toFixed(2)} km/L</dd>
          <dt>Fonte</dt>
          <dd>{consumo.fonte === "historico" ? `Histórico real (${consumo.amostras} amostras)` : "Tabela nominal (sem dado real suficiente)"}</dd>
        </dl>
      )}
    </section>
  );
}
