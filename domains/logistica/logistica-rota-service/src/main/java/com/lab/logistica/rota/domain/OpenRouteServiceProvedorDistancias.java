package com.lab.logistica.rota.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Distância rodoviária real via Matrix API da OpenRouteService (ORS), perfil
 * {@code driving-hgv} (caminhão pesado/heavy goods vehicle) -- substitui a linha reta de
 * {@link HaversineProvedorDistancias}, a troca que DESIGN.md e o README já apontavam como
 * necessária antes de usar isto em decisão real de rota de carga pesada (uma distância
 * errada vira um frete errado). Ativada por {@code app.rota.distancia-provider=openrouteservice}
 * (default continua {@code haversine}) -- trocar de provedor é configuração, não código,
 * porque os dois implementam {@link ProvedorDistancias} (DIP).
 *
 * <p>A lógica pura (montar o corpo da requisição, interpretar a resposta) está isolada em
 * métodos estáticos package-private ({@link #montarCorpoRequisicao} /
 * {@link #extrairMatrizKm}) para poder ser testada sem depender de rede -- só a chamada
 * HTTP em si ({@link #matrizKm}) depende do {@link RestTemplate}.
 */
@Component
@ConditionalOnProperty(name = "app.rota.distancia-provider", havingValue = "openrouteservice")
public class OpenRouteServiceProvedorDistancias implements ProvedorDistancias {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final String perfil;

    @Autowired
    public OpenRouteServiceProvedorDistancias(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.rota.ors.api-key:}") String apiKey,
            @Value("${app.rota.ors.base-url:https://api.openrouteservice.org}") String baseUrl,
            @Value("${app.rota.ors.perfil:driving-hgv}") String perfil,
            @Value("${app.rota.ors.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.rota.ors.read-timeout-ms:10000}") long readTimeoutMs) {
        this(criarRestTemplate(restTemplateBuilder, connectTimeoutMs, readTimeoutMs), apiKey, baseUrl, perfil);
    }

    /** Construtor com o {@link RestTemplate} já pronto -- usado pelos testes (MockRestServiceServer). */
    OpenRouteServiceProvedorDistancias(RestTemplate restTemplate, String apiKey, String baseUrl, String perfil) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "app.rota.distancia-provider=openrouteservice exige app.rota.ors.api-key "
                            + "(variável de ambiente APP_ROTA_ORS_API_KEY) -- crie uma chave grátis em "
                            + "openrouteservice.org/dev/#/signup antes de subir com este provedor.");
        }
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.perfil = perfil;
    }

    /** Sem timeout, uma ORS travada seguraria a thread da requisição para sempre. */
    private static RestTemplate criarRestTemplate(RestTemplateBuilder builder, long connectMs, long readMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectMs))
                .setReadTimeout(Duration.ofMillis(readMs))
                .build();
    }

    @Override
    public double[][] matrizKm(List<Coordenada> pontos) {
        int n = pontos.size();
        if (n < 2) {
            // Mesmo contrato de HaversineProvedorDistancias: matriz trivial sem chamar a rede.
            return new double[n][n];
        }
        String url = baseUrl + "/v2/matrix/" + perfil;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", apiKey);
        String corpo = montarCorpoRequisicao(pontos, mapper);
        try {
            var resposta = restTemplate.postForEntity(url, new HttpEntity<>(corpo, headers), String.class);
            return extrairMatrizKm(resposta.getBody(), mapper, n);
        } catch (RestClientException e) {
            throw new ProvedorDistanciasIndisponivelException(
                    "Falha ao consultar a OpenRouteService (" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Corpo esperado pela Matrix API: {@code locations} em {@code [longitude, latitude]} --
     * ordem invertida em relação a {@link Coordenada} (que é lat/lng), e o erro mais comum
     * ao integrar com APIs de mapa. Pede {@code units: "km"} para não ter que converter de
     * metros na resposta.
     */
    static String montarCorpoRequisicao(List<Coordenada> pontos, ObjectMapper mapper) {
        List<double[]> locations = pontos.stream()
                .map(p -> new double[]{p.lng(), p.lat()})
                .toList();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("locations", locations);
        corpo.put("metrics", List.of("distance"));
        corpo.put("units", "km");
        try {
            return mapper.writeValueAsString(corpo);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar a requisição para a OpenRouteService", e);
        }
    }

    /**
     * Extrai a matriz NxN de km de {@code {"distances": [[...], ...]}}. A ORS devolve
     * {@code null} numa célula quando não acha rota entre dois pontos (fora de alcance do
     * perfil, ponto isolado da malha viária) -- isto é reportado como erro em vez de virar
     * um {@code 0.0} ou {@code NaN} silencioso que contaminaria o solver.
     */
    @SuppressWarnings("unchecked")
    static double[][] extrairMatrizKm(String corpoResposta, ObjectMapper mapper, int n) {
        Map<String, Object> json;
        try {
            json = mapper.readValue(corpoResposta, Map.class);
        } catch (Exception e) {
            throw new ProvedorDistanciasIndisponivelException("Resposta da OpenRouteService não é um JSON válido: " + corpoResposta, e);
        }
        Object distancesObj = json.get("distances");
        if (!(distancesObj instanceof List<?> linhas) || linhas.size() != n) {
            throw new ProvedorDistanciasIndisponivelException(
                    "Resposta da OpenRouteService não tem o formato esperado (esperava distances " + n + "x" + n
                            + "): " + corpoResposta);
        }
        double[][] matriz = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (!(linhas.get(i) instanceof List<?> linha) || linha.size() != n) {
                throw new ProvedorDistanciasIndisponivelException("Linha " + i + " da matriz da OpenRouteService não tem " + n + " colunas");
            }
            for (int j = 0; j < n; j++) {
                Object valor = linha.get(j);
                if (valor == null) {
                    throw new ProvedorDistanciasIndisponivelException(
                            "OpenRouteService não achou rota entre os pontos " + i + " e " + j
                                    + " (fora de alcance do perfil driving-hgv, ou ponto isolado da malha viária)");
                }
                matriz[i][j] = ((Number) valor).doubleValue();
            }
        }
        return matriz;
    }
}
