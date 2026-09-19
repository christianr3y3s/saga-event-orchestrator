package com.lab.logistica.importacao.dominio;

import com.lab.logistica.importacao.api.dto.AlertaConsumo;
import com.lab.logistica.importacao.api.dto.ImportacaoResumo;
import com.lab.logistica.importacao.api.dto.LinhaRejeitada;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato de colunas (linha 1 = cabeçalho, ordem livre, nomes normalizados -- sem
 * acento, minúsculo, espaços viram "_"): ver README.md deste módulo para a lista completa
 * e um exemplo de planilha.
 *
 * <p>Cada linha é validada de forma independente: uma linha inválida é rejeitada com o
 * motivo, as demais continuam sendo importadas -- uma planilha real de centenas de
 * entregas não deveria ser descartada inteira por um erro de digitação numa célula.
 */
@Service
public class PlanilhaImportService {

    static final List<String> COLUNAS_OBRIGATORIAS = List.of("data", "caminhao", "carga_toneladas", "distancia_km");

    private final EntregaHistoricoRepository repository;

    public PlanilhaImportService(EntregaHistoricoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportacaoResumo importar(String nomeArquivo, InputStream conteudo) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(conteudo)) {
            Sheet planilha = workbook.getSheetAt(0);
            Row cabecalho = planilha.getRow(planilha.getFirstRowNum());
            if (cabecalho == null) {
                throw new IllegalArgumentException("Planilha vazia: não há linha de cabeçalho");
            }

            Map<String, Integer> colunas = mapearColunas(cabecalho);
            List<String> faltando = COLUNAS_OBRIGATORIAS.stream().filter(c -> !colunas.containsKey(c)).toList();
            if (!faltando.isEmpty()) {
                throw new IllegalArgumentException(
                        "Colunas obrigatórias ausentes no cabeçalho: " + String.join(", ", faltando));
            }

            List<EntregaHistorico> validas = new ArrayList<>();
            List<LinhaRejeitada> erros = new ArrayList<>();
            List<AlertaConsumo> alertas = new ArrayList<>();
            int totalLinhas = 0;
            Instant agora = Instant.now();

            for (int i = cabecalho.getRowNum() + 1; i <= planilha.getLastRowNum(); i++) {
                Row linha = planilha.getRow(i);
                if (isLinhaVazia(linha)) {
                    continue;
                }
                totalLinhas++;
                int numeroDeExibicao = i + 1; // 1-based, igual ao que o Excel mostra

                try {
                    EntregaHistorico entrega = lerLinha(linha, colunas, nomeArquivo, numeroDeExibicao, agora);
                    validas.add(entrega);
                    if (entrega.getConsumoKmL() != null && entrega.getConsumoKmL() < Consumo.LIMITE_MIN_KM_L) {
                        alertas.add(new AlertaConsumo(numeroDeExibicao, entrega.getCaminhao(), entrega.getConsumoKmL()));
                    }
                } catch (IllegalArgumentException e) {
                    erros.add(new LinhaRejeitada(numeroDeExibicao, e.getMessage()));
                }
            }

            repository.saveAll(validas);

            return new ImportacaoResumo(nomeArquivo, totalLinhas, validas.size(), erros.size(), erros, alertas);
        }
    }

    private Map<String, Integer> mapearColunas(Row cabecalho) {
        Map<String, Integer> colunas = new HashMap<>();
        for (Cell celula : cabecalho) {
            String nome = CelulaLeitor.normalizarCabecalho(CelulaLeitor.textoOuNulo(celula));
            if (!nome.isEmpty()) {
                colunas.put(nome, celula.getColumnIndex());
            }
        }
        return colunas;
    }

    private boolean isLinhaVazia(Row linha) {
        if (linha == null) {
            return true;
        }
        for (Cell celula : linha) {
            if (CelulaLeitor.textoOuNulo(celula) != null) {
                return false;
            }
        }
        return true;
    }

    private Cell celula(Row linha, Map<String, Integer> colunas, String nome) {
        Integer indice = colunas.get(nome);
        return indice == null ? null : linha.getCell(indice);
    }

    private EntregaHistorico lerLinha(Row linha, Map<String, Integer> colunas, String arquivo, int numeroDeExibicao,
                                       Instant importadoEm) {
        LocalDate data = CelulaLeitor.dataOuNulo(celula(linha, colunas, "data"), "data");
        if (data == null) {
            throw new IllegalArgumentException("data é obrigatória");
        }

        String caminhaoBruto = CelulaLeitor.textoOuNulo(celula(linha, colunas, "caminhao"));
        if (caminhaoBruto == null) {
            throw new IllegalArgumentException("caminhao é obrigatório");
        }
        String caminhao = caminhaoBruto.trim().toUpperCase().replace(' ', '_');
        if (!ModelosCaminhaoConhecidos.ehValido(caminhao)) {
            throw new IllegalArgumentException("caminhao desconhecido: \"" + caminhaoBruto
                    + "\" (esperado um de " + ModelosCaminhaoConhecidos.NOMES_VALIDOS + ")");
        }

        Double cargaToneladas = CelulaLeitor.numeroOuNulo(celula(linha, colunas, "carga_toneladas"), "carga_toneladas");
        if (cargaToneladas == null) {
            throw new IllegalArgumentException("carga_toneladas é obrigatória");
        }
        if (cargaToneladas < 0) {
            throw new IllegalArgumentException("carga_toneladas não pode ser negativa");
        }

        Double distanciaKm = CelulaLeitor.numeroOuNulo(celula(linha, colunas, "distancia_km"), "distancia_km");
        if (distanciaKm == null) {
            throw new IllegalArgumentException("distancia_km é obrigatória");
        }
        if (distanciaKm <= 0) {
            throw new IllegalArgumentException("distancia_km deve ser maior que zero");
        }

        Double consumoKmL = CelulaLeitor.numeroOuNulo(celula(linha, colunas, "consumo_kml"), "consumo_kml");
        if (consumoKmL != null && consumoKmL <= 0) {
            throw new IllegalArgumentException("consumo_kml deve ser maior que zero quando informado");
        }

        BigDecimal custoDiesel = campoDecimalNaoNegativo(linha, colunas, "custo_diesel");
        BigDecimal custoOperacional = campoDecimalNaoNegativo(linha, colunas, "custo_operacional");
        BigDecimal pedagios = campoDecimalNaoNegativo(linha, colunas, "pedagios");
        BigDecimal freteCobrado = campoDecimalNaoNegativo(linha, colunas, "frete_cobrado");

        String origem = CelulaLeitor.textoOuNulo(celula(linha, colunas, "origem"));
        String destino = CelulaLeitor.textoOuNulo(celula(linha, colunas, "destino"));

        return new EntregaHistorico(data, caminhao, cargaToneladas, distanciaKm, consumoKmL, custoDiesel,
                custoOperacional, pedagios, freteCobrado, origem, destino, arquivo, numeroDeExibicao, importadoEm);
    }

    private BigDecimal campoDecimalNaoNegativo(Row linha, Map<String, Integer> colunas, String nome) {
        BigDecimal valor = CelulaLeitor.decimalOuNulo(celula(linha, colunas, nome), nome);
        if (valor != null && valor.signum() < 0) {
            throw new IllegalArgumentException(nome + " não pode ser negativo");
        }
        return valor;
    }
}
