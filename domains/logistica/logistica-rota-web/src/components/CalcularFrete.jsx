import { useState } from "react";
import { calcularFrete } from "../api/logisticaApi.js";

const CAMINHOES = ["RODOTREM", "CARRETA_4_EIXOS", "CARRETA_30T"];

const CAMPOS_NUMERICOS = [
  ["cargaToneladas", "Carga (toneladas)"],
  ["distanciaKm", "Distância (km)"],
  ["precoDieselPorLitro", "Preço do diesel (R$/L)"],
  ["pedagios", "Pedágios (R$)"],
  ["custoOperacionalPorKm", "Custo operacional (R$/km)"],
  ["margemPercentual", "Margem (%)"],
  ["pisoMinimo", "Piso mínimo (R$, opcional)"]
];

const VALORES_INICIAIS = {
  cargaToneladas: "0",
  distanciaKm: "0",
  precoDieselPorLitro: "6.20",
  pedagios: "0",
  custoOperacionalPorKm: "3.00",
  margemPercentual: "0",
  pisoMinimo: ""
};

export function CalcularFrete({ apiBase }) {
  const [caminhao, setCaminhao] = useState(CAMINHOES[0]);
  const [valores, setValores] = useState(VALORES_INICIAIS);
  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState(null);
  const [carregando, setCarregando] = useState(false);

  function aoMudarCampo(nome, valor) {
    setValores((v) => ({ ...v, [nome]: valor }));
  }

  async function aoEnviar(e) {
    e.preventDefault();
    setErro(null);
    setResultado(null);
    setCarregando(true);
    try {
      const r = await calcularFrete(apiBase, {
        caminhao,
        cargaToneladas: Number(valores.cargaToneladas),
        distanciaKm: Number(valores.distanciaKm),
        precoDieselPorLitro: Number(valores.precoDieselPorLitro),
        pedagios: Number(valores.pedagios),
        custoOperacionalPorKm: Number(valores.custoOperacionalPorKm),
        margemPercentual: Number(valores.margemPercentual),
        pisoMinimo: valores.pisoMinimo === "" ? null : Number(valores.pisoMinimo)
      });
      setResultado(r);
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <section aria-label="Calcular frete">
      <h2>Calcular frete</h2>
      <form onSubmit={aoEnviar}>
        <label>
          Caminhão
          <select value={caminhao} onChange={(e) => setCaminhao(e.target.value)}>
            {CAMINHOES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </label>
        {CAMPOS_NUMERICOS.map(([nome, rotulo]) => (
          <label key={nome}>
            {rotulo}
            <input
              type="number"
              step="0.01"
              value={valores[nome]}
              onChange={(e) => aoMudarCampo(nome, e.target.value)}
            />
          </label>
        ))}
        <button type="submit" disabled={carregando}>
          {carregando ? "Calculando..." : "Calcular"}
        </button>
      </form>

      {erro && <p role="alert">{erro}</p>}

      {resultado && (
        <dl>
          <dt>Custo total</dt>
          <dd>R$ {resultado.custoTotal}</dd>
          <dt>Frete sugerido</dt>
          <dd>R$ {resultado.frete}</dd>
          <dt>Frete por km</dt>
          <dd>R$ {resultado.fretePorKm}</dd>
          <dt>Frete por tonelada</dt>
          <dd>R$ {resultado.fretePorTonelada}</dd>
          {resultado.abaixoDoPiso && <dd role="alert">Atenção: frete calculado abaixo do piso mínimo.</dd>}
        </dl>
      )}
    </section>
  );
}
