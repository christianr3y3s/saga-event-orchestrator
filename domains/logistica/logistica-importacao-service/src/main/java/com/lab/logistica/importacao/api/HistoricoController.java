package com.lab.logistica.importacao.api;

import com.lab.logistica.importacao.api.dto.ConsumoMedioResponse;
import com.lab.logistica.importacao.api.dto.EntregaHistoricoDto;
import com.lab.logistica.importacao.dominio.DatasetCsvExporter;
import com.lab.logistica.importacao.dominio.EntregaHistoricoRepository;
import com.lab.logistica.importacao.dominio.HistoricoConsumoService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoricoController {

    private final EntregaHistoricoRepository repository;
    private final HistoricoConsumoService historicoConsumoService;

    public HistoricoController(EntregaHistoricoRepository repository, HistoricoConsumoService historicoConsumoService) {
        this.repository = repository;
        this.historicoConsumoService = historicoConsumoService;
    }

    /**
     * Consumo médio calibrado com dados reais (ou nominal da tabela, quando ainda não há
     * histórico suficiente) -- é o insumo que logistica-rota-service usaria no lugar (ou
     * ao lado) do consumo nominal fixo, se/quando os dois forem integrados.
     */
    @GetMapping("/historico/consumo-medio")
    public ConsumoMedioResponse consumoMedio(@RequestParam String caminhao, @RequestParam double cargaToneladas) {
        return historicoConsumoService.consumoMedio(caminhao, cargaToneladas);
    }

    /** Todo o histórico importado, como JSON -- sem paginação ainda; ver README. */
    @GetMapping("/historico")
    public List<EntregaHistoricoDto> listar() {
        return repository.findAll().stream().map(EntregaHistoricoDto::deDominio).toList();
    }

    /** Mesmo histórico em CSV, para um pipeline externo de treinamento de IA consumir direto. */
    @GetMapping(value = "/historico/dataset.csv", produces = "text/csv")
    public ResponseEntity<String> dataset() {
        String csv = DatasetCsvExporter.exportar(repository.findAll());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"entregas_historico.csv\"")
                .body(csv);
    }
}
