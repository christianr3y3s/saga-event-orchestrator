import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { Historico } from "../../src/components/Historico.jsx";

describe("Historico", () => {
  beforeEach(() => {
    window.fetch = jasmine.createSpy("fetch");
  });

  it("lista as entregas retornadas por GET /historico", async () => {
    window.fetch.and.resolveTo({
      ok: true,
      status: 200,
      text: async () => JSON.stringify([
        { id: 1, dataEntrega: "2026-01-10", caminhao: "RODOTREM", origem: "Belém", destino: "Rio de Janeiro",
          distanciaKm: 3125, consumoKmL: 2.8, freteCobrado: "18000.00" }
      ])
    });

    render(<Historico apiBase="http://x" />);
    fireEvent.click(screen.getByRole("button", { name: /carregar histórico/i }));

    expect(await screen.findByText("Belém")).toBeTruthy();
    expect(await screen.findByText("Rio de Janeiro")).toBeTruthy();
  });

  it("mostra uma mensagem quando não há entregas importadas", async () => {
    window.fetch.and.resolveTo({ ok: true, status: 200, text: async () => JSON.stringify([]) });

    render(<Historico apiBase="http://x" />);
    fireEvent.click(screen.getByRole("button", { name: /carregar histórico/i }));

    expect(await screen.findByText(/nenhuma entrega importada/i)).toBeTruthy();
  });

  it("consulta o consumo médio calibrado e mostra a fonte do dado", async () => {
    window.fetch.and.resolveTo({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ caminhao: "RODOTREM", cargaToneladas: 10, consumoKmL: 2.6, amostras: 42, fonte: "historico" })
    });

    render(<Historico apiBase="http://x" />);
    fireEvent.click(screen.getByRole("button", { name: /consultar/i }));

    await waitFor(() => expect(screen.getByText(/2\.60 km\/L/)).toBeTruthy());
    expect(screen.getByText(/Histórico real \(42 amostras\)/)).toBeTruthy();
  });
});
