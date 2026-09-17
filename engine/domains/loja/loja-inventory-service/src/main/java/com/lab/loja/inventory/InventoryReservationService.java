package com.lab.loja.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesmo desenho de CashbackApplicationService (domains/cashback/cashback-outbox-jpa):
 * a mutação de negócio e o registro do evento de saída acontecem na MESMA transação de
 * banco. Se o outbox.save() falhar (ex.: constraint UNIQUE de (correlationId, eventType)
 * batendo sob concorrência), o Spring desfaz a transação inteira -- inclusive os
 * decrementos/incrementos de estoque já aplicados nesta chamada. É essa propriedade do
 * @Transactional, não a checagem otimista abaixo, quem garante que nunca sobra estoque
 * decrementado sem o evento correspondente publicado (ou vice-versa).
 */
@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private static final String EVT_RESERVADO = "EstoqueReservado";
    private static final String EVT_INDISPONIVEL = "EstoqueIndisponivel";
    private static final String EVT_LIBERADO = "EstoqueLiberado";

    public enum ReserveResult { RESERVED, INSUFFICIENT_STOCK, ALREADY_PROCESSED }

    private final StockItemRepository stockItems;
    private final InventoryOutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public InventoryReservationService(StockItemRepository stockItems,
                                        InventoryOutboxEventRepository outbox,
                                        ObjectMapper mapper) {
        this.stockItems = stockItems;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public ReserveResult reserveStock(String correlationId, String orderId, List<ItemRequest> items) {
        // Checagem otimista (mesma ressalva do cashback): evita trabalho à toa no caso
        // comum de redelivery. Quem garante idempotência de verdade sob concorrência é a
        // constraint UNIQUE(correlationId, eventType) no insert do outbox, propagando a
        // exceção pra fora do método -- deixamos de propósito, sem try/catch aqui dentro.
        boolean jaReservado = outbox.findByCorrelationIdAndEventType(correlationId, EVT_RESERVADO).isPresent();
        boolean jaIndisponivel = outbox.findByCorrelationIdAndEventType(correlationId, EVT_INDISPONIVEL).isPresent();
        if (jaReservado || jaIndisponivel) {
            log.info("reservation for {} already processed (found by pre-check), skipping", correlationId);
            return ReserveResult.ALREADY_PROCESSED;
        }

        // Carrega todos os itens ANTES de decrementar qualquer um. É "tudo ou nada":
        // se faltasse estoque pro terceiro item depois de já ter decrementado os dois
        // primeiros, teríamos que desfazer manualmente -- em vez disso, validamos a
        // disponibilidade de TODOS os itens primeiro, e só então aplicamos os decrementos.
        Map<String, StockItem> carregados = new LinkedHashMap<>();
        for (ItemRequest item : items) {
            StockItem stock = stockItems.findById(item.sku())
                    .orElseGet(() -> new StockItem(item.sku(), 0));
            carregados.put(item.sku(), stock);
        }

        boolean todosDisponiveis = items.stream()
                .allMatch(item -> carregados.get(item.sku()).getAvailableQuantity() >= item.quantity());

        if (!todosDisponiveis) {
            outbox.save(new InventoryOutboxEvent(correlationId, EVT_INDISPONIVEL,
                    toPayload(EVT_INDISPONIVEL, correlationId, orderId, items)));
            return ReserveResult.INSUFFICIENT_STOCK;
        }

        for (ItemRequest item : items) {
            StockItem stock = carregados.get(item.sku());
            stock.setAvailableQuantity(stock.getAvailableQuantity() - item.quantity());
            stockItems.save(stock);
        }

        outbox.save(new InventoryOutboxEvent(correlationId, EVT_RESERVADO,
                toPayload(EVT_RESERVADO, correlationId, orderId, items)));
        return ReserveResult.RESERVED;
    }

    /**
     * Compensação da saga (acionada em PagamentoRecusado): devolve ao estoque os itens
     * reservados por este correlationId. Idempotente por (correlationId, "EstoqueLiberado")
     * -- mesma garantia de constraint UNIQUE do método acima.
     */
    @Transactional
    public void releaseStock(String correlationId, String orderId, List<ItemRequest> items) {
        if (outbox.findByCorrelationIdAndEventType(correlationId, EVT_LIBERADO).isPresent()) {
            log.info("release for {} already processed, skipping", correlationId);
            return;
        }

        for (ItemRequest item : items) {
            StockItem stock = stockItems.findById(item.sku())
                    .orElseGet(() -> new StockItem(item.sku(), 0));
            stock.setAvailableQuantity(stock.getAvailableQuantity() + item.quantity());
            stockItems.save(stock);
        }

        outbox.save(new InventoryOutboxEvent(correlationId, EVT_LIBERADO,
                toPayload(EVT_LIBERADO, correlationId, orderId, items)));
    }

    private String toPayload(String eventType, String correlationId, String orderId, List<ItemRequest> items) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", eventType);
            map.put("correlationId", correlationId);
            map.put("orderId", orderId);
            map.put("items", items);
            map.put("atMs", System.currentTimeMillis());
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize outbox payload", e);
        }
    }
}
