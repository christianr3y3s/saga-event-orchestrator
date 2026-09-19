package com.lab.logistica.importacao.api;

import com.lab.logistica.importacao.api.dto.ImportacaoResumo;
import com.lab.logistica.importacao.dominio.PlanilhaImportService;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImportacaoController {

    private final PlanilhaImportService importService;

    public ImportacaoController(PlanilhaImportService importService) {
        this.importService = importService;
    }

    /**
     * Recebe um .xlsx (multipart/form-data, campo "arquivo") de entregas/custos.
     * Ver README para o contrato de colunas. Linhas inválidas são reportadas, não
     * abortam a importação inteira.
     */
    @PostMapping(value = "/importacoes/planilhas", consumes = "multipart/form-data")
    public ImportacaoResumo importar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        var resumo = importService.importar(arquivo.getOriginalFilename(), arquivo.getInputStream());
        return ImportacaoResumo.deDominio(resumo);
    }
}
