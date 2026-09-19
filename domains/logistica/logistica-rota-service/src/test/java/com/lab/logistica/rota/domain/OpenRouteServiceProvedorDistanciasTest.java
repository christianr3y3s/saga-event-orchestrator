package com.lab.logistica.rota.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testa só a lógica pura (montar requisição, interpretar resposta) -- sem rede, sem mockar
 * RestTemplate. A chamada HTTP em si ({@code matrizKm}) não tem teste automatizado aqui;
 * ver a ressalva de verificação no README deste módulo.
 */
class OpenRouteServiceProvedorDistanciasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<Coordenada> pontos = List.of(
            new Coordenada(-23.55, -46.63), // São Paulo
            new Coordenada(-22.90, -43.17)  // Rio de Janeiro
    );

    @Test
    void corpoDaRequisicaoInverteParaLongitudeLatitude() throws Exception {
        String json = OpenRouteServiceProvedorDistancias.montarCorpoRequisicao(pontos, mapper);
        var corpo = mapper.readTree(json);

        assertEquals(-46.63, corpo.get("locations").get(0).get(0).asDouble(), 1e-9); // lng primeiro
        assertEquals(-23.55, corpo.get("locations").get(0).get(1).asDouble(), 1e-9); // lat depois
        assertEquals("distance", corpo.get("metrics").get(0).asText());
        assertEquals("km", corpo.get("units").asText());
    }

    @Test
    void extraiMatrizDaRespostaValida() {
        String resposta = "{\"distances\":[[0.0,429.3],[429.3,0.0]],\"metadata\":{}}";
        double[][] m = OpenRouteServiceProvedorDistancias.extrairMatrizKm(resposta, mapper, 2);

        assertEquals(0.0, m[0][0]);
        assertEquals(429.3, m[0][1], 1e-9);
        assertEquals(429.3, m[1][0], 1e-9);
    }

    @Test
    void rejeitaRespostaSemCampoDistances() {
        String resposta = "{\"metadata\":{}}";
        var ex = assertThrows(IllegalStateException.class,
                () -> OpenRouteServiceProvedorDistancias.extrairMatrizKm(resposta, mapper, 2));
        assertTrue(ex.getMessage().contains("distances"));
    }

    @Test
    void rejeitaMatrizComTamanhoErrado() {
        String resposta = "{\"distances\":[[0.0,1.0,2.0]]}"; // 1x3 em vez de 2x2
        assertThrows(IllegalStateException.class,
                () -> OpenRouteServiceProvedorDistancias.extrairMatrizKm(resposta, mapper, 2));
    }

    @Test
    void rejeitaCelulaNulaComMensagemExplicativa() {
        // ORS devolve null quando não acha rota entre dois pontos -- isto não pode virar
        // 0.0/NaN silencioso, tem que ser um erro explícito.
        String resposta = "{\"distances\":[[0.0,null],[null,0.0]]}";
        var ex = assertThrows(IllegalStateException.class,
                () -> OpenRouteServiceProvedorDistancias.extrairMatrizKm(resposta, mapper, 2));
        assertTrue(ex.getMessage().contains("não achou rota"));
    }

    @Test
    void rejeitaRespostaComJsonInvalido() {
        assertThrows(IllegalStateException.class,
                () -> OpenRouteServiceProvedorDistancias.extrairMatrizKm("isto não é json", mapper, 2));
    }

    @Test
    void rejeitaLinhaQueNaoEhLista() {
        assertThrows(IllegalStateException.class,
                () -> OpenRouteServiceProvedorDistancias.extrairMatrizKm("{\"distances\":[1,2]}", mapper, 2));
    }

    @Test
    void falhaAoSerializarCorpoViraErroExplicito() throws Exception {
        ObjectMapper quebrado = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(quebrado.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") { });

        var ex = assertThrows(IllegalStateException.class,
                () -> OpenRouteServiceProvedorDistancias.montarCorpoRequisicao(pontos, quebrado));
        assertTrue(ex.getMessage().contains("montar a requisição"));
    }
}
