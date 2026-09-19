package com.lab.logistica.rota.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.logistica.rota.api.dto.CoordenadaDto;
import com.lab.logistica.rota.api.dto.SimularRotaRequest;
import com.lab.logistica.rota.domain.HaversineProvedorDistancias;
import com.lab.logistica.rota.domain.OpenRouteServiceProvedorDistancias;
import com.lab.logistica.rota.domain.ProvedorDistancias;
import com.lab.logistica.rota.domain.TipoCaminhao;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Prontidão de produção: health check, respostas de erro e a troca de provedor por configuração. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProducaoReadinessTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthCheckResponde200EUp() {
        ResponseEntity<String> r = rest.getForEntity("/actuator/health", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void endpointsSensiveisDoActuatorNaoSaoExpostos() {
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/actuator/beans", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void caminhaoAusenteRetorna400ComMensagem() {
        var req = new SimularRotaRequest(null, 10.0, List.of(new CoordenadaDto(-23, -46), new CoordenadaDto(-22, -43)));
        ResponseEntity<String> r = rest.postForEntity("/rotas/simular", req, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("caminhao");
    }

    @Test
    void demaisPontosQueOTetoRetorna400() {
        var pontos = new java.util.ArrayList<CoordenadaDto>();
        for (int i = 0; i < 51; i++) {
            pontos.add(new CoordenadaDto(-23 + i * 0.01, -46));
        }
        ResponseEntity<String> r = rest.postForEntity("/rotas/simular",
                new SimularRotaRequest(TipoCaminhao.CARRETA_30T, 10.0, pontos), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("Máximo de 50 pontos");
    }

    // --- troca de provedor por configuração (o que APP_ROTA_DISTANCIA_PROVIDER faz em produção) ---

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpMessageConvertersAutoConfiguration.class, RestTemplateAutoConfiguration.class))
            .withUserConfiguration(HaversineProvedorDistancias.class, OpenRouteServiceProvedorDistancias.class);

    @Test
    void defaultEhHaversineSemPrecisarDeChave() {
        runner.withPropertyValues("app.rota.distancia-provider=haversine", "app.rota.fator-rodoviario=1.3")
                .run(ctx -> assertThat(ctx).hasSingleBean(HaversineProvedorDistancias.class)
                        .doesNotHaveBean(OpenRouteServiceProvedorDistancias.class));
    }

    @Test
    void openrouteserviceSobeComChave() {
        runner.withPropertyValues("app.rota.distancia-provider=openrouteservice", "app.rota.ors.api-key=k",
                        "app.rota.fator-rodoviario=1.3")
                .run(ctx -> assertThat(ctx).hasSingleBean(ProvedorDistancias.class)
                        .hasSingleBean(OpenRouteServiceProvedorDistancias.class));
    }

    @Test
    void openrouteserviceSemChaveFalhaNaInicializacaoEmVezDeFalharNoPrimeiroRequest() {
        runner.withPropertyValues("app.rota.distancia-provider=openrouteservice", "app.rota.fator-rodoviario=1.3")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasStackTraceContaining("APP_ROTA_ORS_API_KEY");
                });
    }
}
