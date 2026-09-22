import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { SimularRota } from "../../src/components/SimularRota.jsx";

describe("SimularRota", () => {
  beforeEach(() => {
    window.fetch = jasmine.createSpy("fetch");
  });

  it("mostra o resultado da simulação após enviar o formulário", async () => {
    window.fetch.and.resolveTo({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ ordem: [], distanciaKm: 3125, consumoKmL: 2.85, litros: 1096.5 })
    });

    render(<SimularRota apiBase="http://x" />);

    fireEvent.change(screen.getByLabelText(/pontos/i), {
      target: { value: "-1.4558,-48.4902\n-22.9068,-43.1729" }
    });
    fireEvent.click(screen.getByRole("button", { name: /simular/i }));

    await waitFor(() => expect(screen.getByText(/3125\.00 km/)).toBeTruthy());
    expect(window.fetch).toHaveBeenCalledWith(
      "http://x/rotas/simular",
      jasmine.objectContaining({ method: "POST" })
    );
  });

  it("mostra uma mensagem de erro sem chamar a API quando um ponto está mal formatado", async () => {
    render(<SimularRota apiBase="http://x" />);

    fireEvent.change(screen.getByLabelText(/pontos/i), { target: { value: "não é um ponto" } });
    fireEvent.click(screen.getByRole("button", { name: /simular/i }));

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(window.fetch).not.toHaveBeenCalled();
  });

  it("mostra a mensagem de erro do back-end quando a chamada falha", async () => {
    window.fetch.and.resolveTo({
      ok: false,
      status: 422,
      text: async () => JSON.stringify({ mensagem: "carga acima da capacidade do caminhão" })
    });

    render(<SimularRota apiBase="http://x" />);
    fireEvent.change(screen.getByLabelText(/pontos/i), { target: { value: "-1,-48\n-22,-43" } });
    fireEvent.click(screen.getByRole("button", { name: /simular/i }));

    expect(await screen.findByText("carga acima da capacidade do caminhão")).toBeTruthy();
  });
});
