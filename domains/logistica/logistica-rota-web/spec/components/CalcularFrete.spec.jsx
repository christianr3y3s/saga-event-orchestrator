import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { CalcularFrete } from "../../src/components/CalcularFrete.jsx";

describe("CalcularFrete", () => {
  beforeEach(() => {
    window.fetch = jasmine.createSpy("fetch");
  });

  it("mostra o frete calculado após enviar o formulário", async () => {
    window.fetch.and.resolveTo({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({
        consumoKmL: 2.85, litros: 1096.5, combustivel: "6798.25", operacional: "9375.00",
        pedagios: "0.00", custoTotal: "16548.25", margem: "0.00", frete: "16548.25",
        fretePorKm: "5.30", fretePorTonelada: "344.75", abaixoDoPiso: false
      })
    });

    render(<CalcularFrete apiBase="http://x" />);
    fireEvent.click(screen.getByRole("button", { name: /calcular/i }));

    await waitFor(() => expect(screen.getByText(/R\$ 16548\.25/)).toBeTruthy());
  });

  it("avisa quando o frete calculado fica abaixo do piso mínimo", async () => {
    window.fetch.and.resolveTo({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({
        consumoKmL: 2.85, litros: 10, combustivel: "62.00", operacional: "30.00",
        pedagios: "0.00", custoTotal: "92.00", margem: "0.00", frete: "92.00",
        fretePorKm: "9.20", fretePorTonelada: "0.00", abaixoDoPiso: true
      })
    });

    render(<CalcularFrete apiBase="http://x" />);
    fireEvent.click(screen.getByRole("button", { name: /calcular/i }));

    expect(await screen.findByText(/abaixo do piso mínimo/i)).toBeTruthy();
  });
});
