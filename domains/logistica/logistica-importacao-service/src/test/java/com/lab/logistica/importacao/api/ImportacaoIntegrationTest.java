package com.lab.logistica.importacao.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.logistica.importacao.api.dto.ConsumoMedioResponse;
import com.lab.logistica.importacao.api.dto.EntregaHistoricoDto;
import com.lab.logistica.importacao.api.dto.ImportacaoResumo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Ponta a ponta de verdade: sobe o contexto Spring completo com um Postgres real (via
 * datasource configurado em application.yml/aplicação) e vai até o endpoint HTTP de
 * upload -- não chama o service Java diretamente. Ver README para a ressalva sobre rodar
 * isto contra H2 em modo de compatibilidade PostgreSQL nesta sessão em vez de um Postgres
 * de verdade (src/test/resources/application.yml).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportacaoIntegrationTest {

    @LocalServerPort
    private int porta;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + porta + path;
    }

    private byte[] planilhaXlsx(List<List<String>> linhas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            for (int i = 0; i < linhas.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> valores = linhas.get(i);
                for (int c = 0; c < valores.size(); c++) {
                    row.createCell(c).setCellValue(valores.get(c));
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }

    private ResponseEntity<ImportacaoResumo> enviarPlanilha(byte[] conteudo, String nomeArquivo) {
        ByteArrayResource recurso = new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        };
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", recurso);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entidade = new HttpEntity<>(corpo, headers);

        return rest.postForEntity(url("/importacoes/planilhas"), entidade, ImportacaoResumo.class);
    }

    @Test
    void importaPlanilhaEDepoisApareceNoHistoricoENoCsv() throws IOException {
        byte[] planilha = planilhaXlsx(List.of(
                List.of("data", "caminhao", "carga_toneladas", "distancia_km", "consumo_kml",
                        "custo_diesel", "custo_operacional", "pedagios", "frete_cobrado", "origem", "destino"),
                List.of("2026-03-15", "carreta_4_eixos", "38", "500", "2,6",
                        "6,00", "2,50", "120,00", "2877,39", "SP", "RJ")
        ));

        ResponseEntity<ImportacaoResumo> respostaImportacao = enviarPlanilha(planilha, "entregas-teste.xlsx");

        assertThat(respostaImportacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportacaoResumo resumo = respostaImportacao.getBody();
        assertThat(resumo).isNotNull();
        assertThat(resumo.totalLinhas()).isEqualTo(1);
        assertThat(resumo.importadas()).isEqualTo(1);
        assertThat(resumo.rejeitadas()).isZero();

        ResponseEntity<EntregaHistoricoDto[]> respostaHistorico =
                rest.getForEntity(url("/historico"), EntregaHistoricoDto[].class);
        assertThat(respostaHistorico.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaHistorico.getBody()).isNotEmpty();
        assertThat(respostaHistorico.getBody())
                .anySatisfy(e -> assertThat(e.arquivoOrigem()).isEqualTo("entregas-teste.xlsx"));

        ResponseEntity<String> respostaCsv = rest.getForEntity(url("/historico/dataset.csv"), String.class);
        assertThat(respostaCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaCsv.getBody()).contains("entregas-teste.xlsx");
        assertThat(respostaCsv.getBody()).startsWith("id,data_entrega,caminhao");
    }

    @Test
    void planilhaComColunaObrigatoriaFaltandoRetorna400() throws IOException {
        byte[] planilha = planilhaXlsx(List.of(
                List.of("data", "origem"),
                List.of("2026-03-15", "SP")
        ));

        ResponseEntity<ErroResposta> resp = null;
        ByteArrayResource recurso = new ByteArrayResource(planilha) {
            @Override
            public String getFilename() {
                return "invalida.xlsx";
            }
        };
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", recurso);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        resp = rest.postForEntity(url("/importacoes/planilhas"), new HttpEntity<>(corpo, headers), ErroResposta.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().mensagem()).contains("caminhao");
    }

    @Test
    void consumoMedioSemHistoricoUsaTabelaNominal() {
        ResponseEntity<ConsumoMedioResponse> resp = rest.getForEntity(
                url("/historico/consumo-medio?caminhao=CARRETA_30T&cargaToneladas=0"), ConsumoMedioResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().fonte()).isEqualTo("tabela_nominal");
        assertThat(resp.getBody().consumoKmL()).isEqualTo(3.15);
    }

    @Test
    void consumoMedioComCaminhaoDesconhecidoRetorna400() {
        ResponseEntity<ErroResposta> resp = rest.getForEntity(
                url("/historico/consumo-medio?caminhao=BITREM&cargaToneladas=10"), ErroResposta.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
