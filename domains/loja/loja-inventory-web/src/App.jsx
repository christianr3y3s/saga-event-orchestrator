import { ApiConfig } from "./components/ApiConfig.jsx";
import { EstoqueList } from "./components/EstoqueList.jsx";
import { useEstoqueApiBase } from "./hooks/useApiBase.js";

export function App() {
  const [apiBase, setApiBase] = useEstoqueApiBase();

  return (
    <main>
      <h1>Loja</h1>
      <details>
        <summary>Configuração da API</summary>
        <ApiConfig label="loja-inventory-service" value={apiBase} onChange={setApiBase} />
      </details>
      <EstoqueList apiBase={apiBase} />
    </main>
  );
}
