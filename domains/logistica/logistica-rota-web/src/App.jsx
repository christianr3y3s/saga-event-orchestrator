import { useState } from "react";
import { ApiConfig } from "./components/ApiConfig.jsx";
import { SimularRota } from "./components/SimularRota.jsx";
import { CalcularFrete } from "./components/CalcularFrete.jsx";
import { Historico } from "./components/Historico.jsx";
import { useRotaApiBase, useHistoricoApiBase } from "./hooks/useApiBase.js";

const TELAS = {
  simular: "Simular rota",
  frete: "Calcular frete",
  historico: "Histórico de entregas"
};

export function App() {
  const [tela, setTela] = useState("simular");
  const [rotaApiBase, setRotaApiBase] = useRotaApiBase();
  const [historicoApiBase, setHistoricoApiBase] = useHistoricoApiBase();

  return (
    <main>
      <h1>Logística</h1>

      <details>
        <summary>Configuração da API</summary>
        <ApiConfig label="logistica-rota-service (simular rota / calcular frete)" value={rotaApiBase} onChange={setRotaApiBase} />
        <ApiConfig label="logistica-importacao-service (histórico)" value={historicoApiBase} onChange={setHistoricoApiBase} />
      </details>

      <nav>
        {Object.entries(TELAS).map(([chave, rotulo]) => (
          <button key={chave} type="button" onClick={() => setTela(chave)} disabled={tela === chave}>
            {rotulo}
          </button>
        ))}
      </nav>

      {tela === "simular" && <SimularRota apiBase={rotaApiBase} />}
      {tela === "frete" && <CalcularFrete apiBase={rotaApiBase} />}
      {tela === "historico" && <Historico apiBase={historicoApiBase} />}
    </main>
  );
}
