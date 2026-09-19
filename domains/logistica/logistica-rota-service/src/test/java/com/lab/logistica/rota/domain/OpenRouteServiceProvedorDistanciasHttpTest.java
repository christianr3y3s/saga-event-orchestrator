package com.lab.logistica.rota.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * A chamada HTTP de {@code matrizKm} contra um servidor simulado (MockRestServiceServer):
 * confere URL, cabeçalho de autenticação, corpo enviado e o tratamento de cada tipo de falha.
 * Não substitui um teste contra a ORS real (exige chave) -- ver README, seção "Homologação".
 */
class OpenRouteServiceProvedorDistanciasHttpTest {

    private static final String BASE = "https://ors.exemplo.test";
    private static final List<Coordenada> PONTOS =
            List.of(new Coordenada(-23.55, -46.63), new Coordenada(-22.90, -43.17));

    private RestTemplate restTemplate;
    private MockRestServiceServer servidor;
    private OpenRouteServiceProvedorDistancias provedor;

    @BeforeEach
    void preparar() {
        restTemplate = new RestTemplate();
        servidor = MockRestServiceServer.bindTo(restTemplate).build();
        provedor = new OpenRouteServiceProvedorDistancias(restTemplate, "chave-secreta", BASE + "//", "driving-hgv");
    }

    @Test
    void chamaMatrixApiComPerfilChaveECorpoCorretos() {
        servidor.expect(requestTo(BASE + "/v2/matrix/driving-hgv"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "chave-secreta"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"locations\":[[-46.63,-23.55]")))
                .andRespond(withSuccess("{\"distances\":[[0.0,429.3],[429.3,0.0]]}", MediaType.APPLICATION_JSON));

        double[][] m = provedor.matrizKm(PONTOS);

        assertThat(m[0][1]).isEqualTo(429.3);
        servidor.verify();
    }

    @Test
    void menosDeDoisPontosNaoChamaARede() {
        assertThat(provedor.matrizKm(List.of())).isEmpty();
        assertThat(provedor.matrizKm(List.of(PONTOS.get(0)))).isEqualTo(new double[][]{{0.0}});
        servidor.verify(); // nenhuma requisição esperada nem feita
    }

    @Test
    void erro5xxViraProvedorIndisponivel() {
        servidor.expect(requestTo(BASE + "/v2/matrix/driving-hgv")).andRespond(withServerError());

        assertThatThrownBy(() -> provedor.matrizKm(PONTOS))
                .isInstanceOf(ProvedorDistanciasIndisponivelException.class)
                .hasMessageContaining("OpenRouteService");
    }

    @Test
    void chaveInvalida401ViraProvedorIndisponivel() {
        servidor.expect(requestTo(BASE + "/v2/matrix/driving-hgv")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provedor.matrizKm(PONTOS))
                .isInstanceOf(ProvedorDistanciasIndisponivelException.class);
    }

    @Test
    void respostaComCelulaNulaViraProvedorIndisponivel() {
        servidor.expect(requestTo(BASE + "/v2/matrix/driving-hgv"))
                .andRespond(withSuccess("{\"distances\":[[0.0,null],[null,0.0]]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provedor.matrizKm(PONTOS))
                .isInstanceOf(ProvedorDistanciasIndisponivelException.class)
                .hasMessageContaining("não achou rota");
    }

    @Test
    void linhaDaMatrizComTamanhoErradoEhRejeitada() {
        servidor.expect(requestTo(BASE + "/v2/matrix/driving-hgv"))
                .andRespond(withSuccess("{\"distances\":[[0.0,1.0],[1.0]]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provedor.matrizKm(PONTOS))
                .isInstanceOf(ProvedorDistanciasIndisponivelException.class)
                .hasMessageContaining("Linha 1");
    }

    @Test
    void semChaveDeApiOProvedorNaoSobe() {
        assertThatThrownBy(() -> new OpenRouteServiceProvedorDistancias(
                new RestTemplateBuilder(), " ", BASE, "driving-hgv", 1000, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ROTA_ORS_API_KEY");
        assertThatThrownBy(() -> new OpenRouteServiceProvedorDistancias(
                new RestTemplateBuilder(), null, BASE, "driving-hgv", 1000, 1000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void construtorPublicoAplicaTimeoutsEMontaOProvedor() {
        var p = new OpenRouteServiceProvedorDistancias(
                new RestTemplateBuilder(), "chave", BASE, "driving-hgv", 1000, 2000);
        assertThat(p.matrizKm(List.of())).isEmpty();
    }
}
