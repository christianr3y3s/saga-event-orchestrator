import { simularRota, calcularFrete, listarHistorico, consumoMedio } from "../../src/api/logisticaApi.js";

// Lógica idêntica à verificada manualmente (fora do Jasmine, ver
// domains/logistica/README.md "Limitações do ambiente") com Node + fetch nativo, contra o
// mesmo módulo -- este arquivo é a versão formal em Jasmine, para rodar com
// `npm test` numa máquina com acesso ao registro do npm.

function mockFetch(mapa) {
  window.fetch = jasmine.createSpy("fetch").and.callFake(async (url) => {
    if (!(url in mapa)) throw new Error("URL não mapeada no mock: " + url);
    const { status, body } = mapa[url];
    return {
      ok: status >= 200 && status < 300,
      status,
      text: async () => JSON.stringify(body)
    };
  });
}

describe("logisticaApi", () => {
  describe("simularRota", () => {
    it("faz POST em /rotas/simular e devolve o resultado da simulação", async () => {
      mockFetch({
        "http://x/rotas/simular": {
          status: 200,
          body: { ordem: [{ lat: 1, lng: 2 }], distanciaKm: 100, consumoKmL: 2.5, litros: 40 }
        }
      });

      const resultado = await simularRota("http://x", { caminhao: "RODOTREM", cargaToneladas: 0, pontos: [] });

      expect(resultado.distanciaKm).toBe(100);
      expect(resultado.litros).toBe(40);
      expect(window.fetch).toHaveBeenCalledWith(
        "http://x/rotas/simular",
        jasmine.objectContaining({ method: "POST" })
      );
    });
  });

  describe("calcularFrete", () => {
    it("propaga a mensagem de erro do back-end quando a resposta HTTP não é 2xx", async () => {
      mockFetch({ "http://x/fretes/calcular": { status: 400, body: { mensagem: "carga inválida" } } });

      await expectAsync(
        calcularFrete("http://x", { caminhao: "RODOTREM", cargaToneladas: -1 })
      ).toBeRejectedWithError("carga inválida");
    });
  });

  describe("listarHistorico", () => {
    it("faz GET em /historico e devolve a lista", async () => {
      mockFetch({ "http://x/historico": { status: 200, body: [{ id: 1 }, { id: 2 }] } });

      const resultado = await listarHistorico("http://x");

      expect(resultado.length).toBe(2);
    });
  });

  describe("consumoMedio", () => {
    it("monta a query string com caminhao e cargaToneladas", async () => {
      mockFetch({
        "http://x/historico/consumo-medio?caminhao=RODOTREM&cargaToneladas=10": {
          status: 200,
          body: { consumoKmL: 2.9 }
        }
      });

      const resultado = await consumoMedio("http://x", { caminhao: "RODOTREM", cargaToneladas: 10 });

      expect(resultado.consumoKmL).toBe(2.9);
    });
  });

  describe("tratamento de resposta inválida", () => {
    it("lança um erro claro quando o corpo não é JSON válido", async () => {
      mockFetch({}); // qualquer chamada cai no default abaixo
      window.fetch = jasmine.createSpy("fetch").and.resolveTo({
        ok: false,
        status: 500,
        text: async () => "<html>erro</html>"
      });

      await expectAsync(listarHistorico("http://x")).toBeRejectedWithError(/não é um JSON válido/);
    });
  });
});
