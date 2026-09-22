import { listarEstoque, buscarPorSku } from "../../src/api/estoqueApi.js";

// Lógica idêntica à verificada manualmente fora do Jasmine (ver README, "Limitações do
// ambiente") com Node + fetch nativo, contra o mesmo módulo.

describe("estoqueApi", () => {
  it("lista todos os itens em GET /estoque", async () => {
    window.fetch = jasmine.createSpy("fetch").and.resolveTo({
      ok: true, status: 200, text: async () => JSON.stringify([{ sku: "A", availableQuantity: 10 }])
    });

    const resultado = await listarEstoque("http://x");

    expect(resultado.length).toBe(1);
    expect(window.fetch).toHaveBeenCalledWith("http://x/estoque");
  });

  it("busca um item por SKU", async () => {
    window.fetch = jasmine.createSpy("fetch").and.resolveTo({
      ok: true, status: 200, text: async () => JSON.stringify({ sku: "A", availableQuantity: 10 })
    });

    const resultado = await buscarPorSku("http://x", "A");

    expect(resultado.availableQuantity).toBe(10);
    expect(window.fetch).toHaveBeenCalledWith("http://x/estoque/A");
  });

  it("devolve null (não lança erro) quando o SKU não existe (404)", async () => {
    window.fetch = jasmine.createSpy("fetch").and.resolveTo({ ok: false, status: 404, text: async () => "" });

    const resultado = await buscarPorSku("http://x", "NAO-EXISTE");

    expect(resultado).toBeNull();
  });

  it("lança erro para qualquer outro status HTTP não-2xx", async () => {
    window.fetch = jasmine.createSpy("fetch").and.resolveTo({
      ok: false, status: 500, text: async () => JSON.stringify({ mensagem: "erro interno" })
    });

    await expectAsync(listarEstoque("http://x")).toBeRejectedWithError("erro interno");
  });
});
