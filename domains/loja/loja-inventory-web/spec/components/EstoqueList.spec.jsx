import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { EstoqueList } from "../../src/components/EstoqueList.jsx";

describe("EstoqueList", () => {
  beforeEach(() => {
    window.fetch = jasmine.createSpy("fetch");
  });

  it("lista todos os itens ao clicar em 'Listar todos os itens'", async () => {
    window.fetch.and.resolveTo({
      ok: true, status: 200,
      text: async () => JSON.stringify([{ sku: "SKU-A", availableQuantity: 10 }, { sku: "SKU-B", availableQuantity: 0 }])
    });

    render(<EstoqueList apiBase="http://x" />);
    fireEvent.click(screen.getByRole("button", { name: /listar todos os itens/i }));

    expect(await screen.findByText("SKU-A")).toBeTruthy();
    expect(screen.getByText("SKU-B")).toBeTruthy();
  });

  it("mostra 'nenhum item encontrado' quando a busca por SKU não acha nada (404 -> null)", async () => {
    window.fetch.and.resolveTo({ ok: false, status: 404, text: async () => "" });

    render(<EstoqueList apiBase="http://x" />);
    fireEvent.change(screen.getByLabelText(/buscar por sku/i), { target: { value: "NAO-EXISTE" } });
    fireEvent.click(screen.getByRole("button", { name: /^buscar$/i }));

    expect(await screen.findByText(/nenhum item encontrado/i)).toBeTruthy();
  });

  it("mostra a quantidade disponível quando o SKU é encontrado", async () => {
    window.fetch.and.resolveTo({
      ok: true, status: 200, text: async () => JSON.stringify({ sku: "SKU-A", availableQuantity: 7 })
    });

    render(<EstoqueList apiBase="http://x" />);
    fireEvent.change(screen.getByLabelText(/buscar por sku/i), { target: { value: "SKU-A" } });
    fireEvent.click(screen.getByRole("button", { name: /^buscar$/i }));

    expect(await screen.findByText(/SKU-A: 7 unidades disponíveis/)).toBeTruthy();
  });
});
