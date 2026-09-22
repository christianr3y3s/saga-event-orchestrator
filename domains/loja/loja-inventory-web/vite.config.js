import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Sem build-tool nenhum não dá pra servir JSX no navegador -- Vite é o mínimo necessário
// (dev server + build de produção). Ver README para como rodar sem depender de bundler
// nenhum (não existe essa opção real para React 19 -- ver seção "Limitações do ambiente").
export default defineConfig({
  plugins: [react()],
  server: { port: 5174 }
});
