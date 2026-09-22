package com.lab.loja.inventory.api;

import com.lab.loja.inventory.StockItemRepository;
import com.lab.loja.inventory.api.dto.EstoqueItemDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta somente-leitura do estoque, para o front-end React de loja.
 *
 * <p><b>Decisão de arquitetura (não expor mutação por HTTP):</b> reservar/liberar estoque
 * continua exclusivamente via Kafka ({@code InventoryCommandListener}), dentro da saga, com
 * outbox garantindo idempotência. Esta classe propositalmente NÃO tem nenhum
 * {@code @PostMapping}/{@code @PutMapping} -- expor reserva por HTTP permitiria mudar o
 * estoque por fora da saga, quebrando a garantia de idempotência/compensação que o outbox dá.
 * Ver {@code domains/loja/README.md}, seção "API HTTP (somente leitura)".
 */
@RestController
public class EstoqueController {

    private final StockItemRepository repository;

    public EstoqueController(StockItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/estoque")
    public List<EstoqueItemDto> listar() {
        return repository.findAll().stream().map(EstoqueItemDto::deDominio).toList();
    }

    @GetMapping("/estoque/{sku}")
    public ResponseEntity<EstoqueItemDto> buscarPorSku(@PathVariable String sku) {
        return repository.findById(sku)
                .map(EstoqueItemDto::deDominio)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
