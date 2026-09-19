package com.lab.logistica.rota.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.logistica.rota.api.dto.CoordenadaDto;
import com.lab.logistica.rota.api.dto.SimularRotaRequest;
import com.lab.logistica.rota.domain.HaversineProvedorDistancias;
import com.lab.logistica.rota.domain.ProvedorDistanciasIndisponivelException;
import com.lab.logistica.rota.domain.SimuladorRota;
import com.lab.logistica.rota.domain.TipoCaminhao;
import com.lab.logistica.rota.domain.VizinhoMaisProximoComDoisOpt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RotaControllerValidacaoTest {

    private final RotaController controller = new RotaController(
            new SimuladorRota(new HaversineProvedorDistancias(1.3), new VizinhoMaisProximoComDoisOpt()), 3);

    private static List<CoordenadaDto> pontos(int n) {
        List<CoordenadaDto> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new CoordenadaDto(-23.0 + i * 0.1, -46.0));
        }
        return l;
    }

    @Test
    void aceitaAteOTetoDePontos() {
        var resp = controller.simularRota(new SimularRotaRequest(TipoCaminhao.CARRETA_30T, 10.0, pontos(3)));
        assertThat(resp).isNotNull();
    }

    @Test
    void rejeitaMaisPontosQueOTeto() {
        assertThatThrownBy(() -> controller.simularRota(
                new SimularRotaRequest(TipoCaminhao.CARRETA_30T, 10.0, pontos(4))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Máximo de 3 pontos");
    }

    @Test
    void rejeitaCaminhaoNulo() {
        assertThatThrownBy(() -> controller.simularRota(new SimularRotaRequest(null, 10.0, pontos(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caminhao");
    }

    @Test
    void rejeitaPontosNulosOuComItemNulo() {
        assertThatThrownBy(() -> controller.simularRota(new SimularRotaRequest(TipoCaminhao.RODOTREM, 1.0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.simularRota(new SimularRotaRequest(
                TipoCaminhao.RODOTREM, 1.0, Arrays.asList(new CoordenadaDto(-23, -46), null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tratadorTraduzFalhaDoProvedorEm502SemVazarDetalhes() {
        var resp = new TratadorDeErros().tratarProvedorIndisponivel(
                new ProvedorDistanciasIndisponivelException("segredo interno: https://x?key=abc"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody().mensagem()).doesNotContain("segredo").doesNotContain("key=");
    }

    @Test
    void tratadorTraduzEntradaInvalidaEm400() {
        var resp = new TratadorDeErros().tratarEntradaInvalida(new IllegalArgumentException("ruim"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().mensagem()).isEqualTo("ruim");
    }

    @Test
    void tratadorTraduzCorpoIlegivelEm400() {
        var resp = new TratadorDeErros().tratarCorpoInvalido(null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
