import { useState } from "react";
import { simularRota } from "../api/logisticaApi.js";

const CAMINHOES = ["RODOTREM", "CARRETA_4_EIXOS", "CARRETA_30T"];

function parsePontos(texto) {
  // Uma linha por ponto, "lat,lng" -- formato simples de colar, sem exigir um mapa/JS de UI.
  return texto
    .split("\n")
    .map((linha) => linha.trim())
    .filter(Boolean)
    .map((linha) => {
      const [lat, lng] = linha.split(",").map((v) => Number(v.trim()));
      if (Number.isNaN(lat) || Number.isNaN(lng)) {
        throw new Error(`Ponto inválido: "${linha}" (use o formato "lat,lng")`);
      }
      return { lat, lng };
    });
}

export function SimularRota({ apiBase }) {
  const [caminhao, setCaminhao] = useState(CAMINHOES[0]);
  const [cargaToneladas, setCargaToneladas] = useState("0");
  const [pontosTexto, setPontosTexto] = useState("");
  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState(null);
  const [carregando, setCarregando] = useState(false);

  async function aoEnviar(e) {
    e.preventDefault();
    setErro(null);
    setResultado(null);
    let pontos;
    try {
      pontos = parsePontos(pontosTexto);
    } catch (e2) {
      setErro(e2.message);
      return;
    }
    setCarregando(true);
    try {
      const r = await simularRota(apiBase, { caminhao, cargaToneladas: Number(cargaToneladas), pontos });
      setResultado(r);
    } catch (e3) {
      setErro(e3.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <section aria-label="Simular rota">
      <h2>Simular rota</h2>
      <form onSubmit={aoEnviar}>
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
        <label>
          Pontos (um por linha, "lat,lng")
          <textarea
            rows={5}
            value={pontosTexto}
            onChange={(e) => setPontosTexto(e.target.value)}
            placeholder={"-1.4558,-48.4902\n-22.9068,-43.1729"}
          />
        </label>
        <button type="submit" disabled={carregando}>
          {carregando ? "Simulando..." : "Simular"}
        </button>
      </form>

      {erro && <p role="alert">{erro}</p>}

      {resultado && (
        <dl>
          <dt>Distância</dt>
          <dd>{resultado.distanciaKm.toFixed(2)} km</dd>
          <dt>Consumo</dt>
          <dd>{resultado.consumoKmL.toFixed(2)} km/L</dd>
          <dt>Litros estimados</dt>
          <dd>{resultado.litros.toFixed(2)} L</dd>
        </dl>
      )}
    </section>
  );
}
