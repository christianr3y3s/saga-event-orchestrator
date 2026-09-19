package com.lab.logistica.rota.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.logistica.rota.api.dto.CalcularFreteRequest;
import com.lab.logistica.rota.api.dto.CalcularFreteResponse;
import com.lab.logistica.rota.api.dto.CoordenadaDto;
import com.lab.logistica.rota.api.dto.SimularRotaRequest;
import com.lab.logistica.rota.api.dto.SimularRotaResponse;
import com.lab.logistica.rota.domain.TipoCaminhao;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Marcada como "integration": sobe o contexto Spring inteiro num servidor HTTP real (porta
 * aleatória) e chama os endpoints via TestRestTemplate -- ponta a ponta, sem mocks, no
 * mesmo espírito das *AtomicityTest de cashback/loja (lá é "Spring context + H2 de
 * verdade"; aqui, por não ter persistência, é "Spring context + HTTP de verdade").
 * Serve como o "teste ponta a ponta reproduzível" pedido em docs/adding-a-domain.md.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RotaControllerIntegrationTest {

    @LocalServerPort
    private int porta;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + porta + path;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // ---------- /fretes/calcular ----------

    @Test
    void calcularFrete_casoA_carretaCarregada() {
        var req = new CalcularFreteRequest(TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0,
                bd("6.00"), bd("120.00"), bd("2.50"), bd("15"), null);

        ResponseEntity<CalcularFreteResponse> resp =
                rest.postForEntity(url("/fretes/calcular"), req, CalcularFreteResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        CalcularFreteResponse corpo = resp.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.frete()).isEqualByComparingTo(bd("2877.39"));
        assertThat(corpo.fretePorKm()).isEqualByComparingTo(bd("5.75"));
        assertThat(corpo.fretePorTonelada()).isEqualByComparingTo(bd("75.72"));
        assertThat(corpo.abaixoDoPiso()).isFalse();
    }

    @Test
    void calcularFrete_casoB_carretaVaziaSemFretePorTonelada() {
        var req = new CalcularFreteRequest(TipoCaminhao.CARRETA_30T, 0.0, 300.0,
                bd("6.20"), BigDecimal.ZERO, bd("2.00"), bd("10"), null);

        ResponseEntity<CalcularFreteResponse> resp =
                rest.postForEntity(url("/fretes/calcular"), req, CalcularFreteResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().frete()).isEqualByComparingTo(bd("1309.53"));
        assertThat(resp.getBody().fretePorTonelada()).isNull();
    }

    @Test
    void calcularFrete_casoC_rodotremCarregado() {
        var req = new CalcularFreteRequest(TipoCaminhao.RODOTREM, 48.0, 1000.0,
                bd("6.00"), bd("350.00"), bd("3.00"), bd("12"), null);

        ResponseEntity<CalcularFreteResponse> resp =
                rest.postForEntity(url("/fretes/calcular"), req, CalcularFreteResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().frete()).isEqualByComparingTo(bd("6877.58"));
    }

    @Test
    void calcularFrete_pisoAbaixoDoFreteSinalizaNaResposta() {
        var req = new CalcularFreteRequest(TipoCaminhao.CARRETA_4_EIXOS, 38.0, 500.0,
                bd("6.00"), bd("120.00"), bd("2.50"), bd("15"), bd("3000.00"));

        ResponseEntity<CalcularFreteResponse> resp =
                rest.postForEntity(url("/fretes/calcular"), req, CalcularFreteResponse.class);

        assertThat(resp.getBody().abaixoDoPiso()).isTrue();
    }

    @Test
    void calcularFrete_cargaAcimaDaCapacidadeRetorna400ComMensagem() {
        var req = new CalcularFreteRequest(TipoCaminhao.CARRETA_4_EIXOS, 40.0, 500.0,
                bd("6.00"), bd("120.00"), bd("2.50"), bd("15"), null);

        ResponseEntity<ErroResposta> resp =
                rest.postForEntity(url("/fretes/calcular"), req, ErroResposta.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().mensagem()).contains("capacidade");
    }

    @Test
    void calcularFrete_distanciaZeroRetorna400() {
        var req = new CalcularFreteRequest(TipoCaminhao.CARRETA_4_EIXOS, 38.0, 0.0,
                bd("6.00"), bd("120.00"), bd("2.50"), bd("15"), null);

        ResponseEntity<ErroResposta> resp =
                rest.postForEntity(url("/fretes/calcular"), req, ErroResposta.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- /rotas/simular ----------

    @Test
    void simularRota_tresPontosDevolveCircuitoFechadoEConsumo() {
        var req = new SimularRotaRequest(TipoCaminhao.CARRETA_4_EIXOS, 35.0, List.of(
                new CoordenadaDto(-23.55, -46.63),
                new CoordenadaDto(-22.90, -43.17),
                new CoordenadaDto(-19.92, -43.94)
        ));

        ResponseEntity<SimularRotaResponse> resp =
                rest.postForEntity(url("/rotas/simular"), req, SimularRotaResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        SimularRotaResponse corpo = resp.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.ordem()).hasSize(4); // 3 pontos + retorno à origem
        assertThat(corpo.ordem().get(0)).isEqualTo(corpo.ordem().get(3));
        assertThat(corpo.distanciaKm()).isGreaterThan(0.0);
        assertThat(corpo.litros()).isGreaterThan(0.0);
        double esperado = TipoCaminhao.CARRETA_4_EIXOS.consumoKmL(35.0);
        assertThat(corpo.consumoKmL()).isEqualTo(esperado);
    }

    @Test
    void simularRota_comMenosDeDoisPontosRetorna400() {
        var req = new SimularRotaRequest(TipoCaminhao.RODOTREM, 0.0,
                List.of(new CoordenadaDto(-23.55, -46.63)));

        ResponseEntity<ErroResposta> resp =
                rest.postForEntity(url("/rotas/simular"), req, ErroResposta.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void simularRota_comCoordenadaInvalidaRetorna400() {
        // Corpo bruto porque CoordenadaDto/Coordenada validam no construtor -- a serialização
        // de um double fora de faixa pelo Jackson dispara IllegalArgumentException, tratado
        // pelo mesmo TratadorDeErros.
        String corpoBruto = "{\"caminhao\":\"RODOTREM\",\"cargaToneladas\":0,\"pontos\":["
                + "{\"lat\":95.0,\"lng\":0.0},{\"lat\":0.0,\"lng\":0.0}]}";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var entidade = new org.springframework.http.HttpEntity<>(corpoBruto, headers);

        ResponseEntity<ErroResposta> resp =
                rest.postForEntity(url("/rotas/simular"), entidade, ErroResposta.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
