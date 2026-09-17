package com.lab.loja.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit puro com Mockito -- nenhum Spring context, nenhum H2. StockItemRepository e
 * InventoryOutboxEventRepository são dublês de teste. Cobre a lógica de decisão de
 * InventoryReservationService isoladamente.
 *
 * O que este arquivo NÃO cobre de propósito: o comportamento real da constraint
 * UNIQUE(correlationId, eventType) sob concorrência -- isso é comportamento do banco,
 * coberto em InventoryReservationServiceAtomicityTest (@Tag("integration")).
 */
@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceMockitoTest {

    @Mock StockItemRepository stockItems;
    @Mock InventoryOutboxEventRepository outbox;

    ObjectMapper mapper = new ObjectMapper();

    InventoryReservationService service;

    @BeforeEach
    void setUp() {
        service = new InventoryReservationService(stockItems, outbox, mapper);
    }

    @Test
    void reservesAllItemsWhenStockIsSufficient() {
        when(outbox.findByCorrelationIdAndEventType("tx-1", "EstoqueReservado")).thenReturn(Optional.empty());
        when(outbox.findByCorrelationIdAndEventType("tx-1", "EstoqueIndisponivel")).thenReturn(Optional.empty());
        when(stockItems.findById("SKU-A")).thenReturn(Optional.of(new StockItem("SKU-A", 10)));
        when(stockItems.findById("SKU-B")).thenReturn(Optional.of(new StockItem("SKU-B", 5)));

        var result = service.reserveStock("tx-1", "order-1",
                List.of(new ItemRequest("SKU-A", 3), new ItemRequest("SKU-B", 5)));

        assertEquals(InventoryReservationService.ReserveResult.RESERVED, result);

        ArgumentCaptor<StockItem> captor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItems, times(2)).save(captor.capture());
        var saved = captor.getAllValues();
        assertEquals(7L, saved.stream().filter(s -> s.getSku().equals("SKU-A")).findFirst().orElseThrow().getAvailableQuantity());
        assertEquals(0L, saved.stream().filter(s -> s.getSku().equals("SKU-B")).findFirst().orElseThrow().getAvailableQuantity());

        ArgumentCaptor<InventoryOutboxEvent> eventCaptor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(outbox).save(eventCaptor.capture());
        assertEquals("EstoqueReservado", eventCaptor.getValue().getEventType());
    }

    @Test
    void doesNotMutateAnyStockWhenOneItemIsInsufficient() {
        when(outbox.findByCorrelationIdAndEventType("tx-2", "EstoqueReservado")).thenReturn(Optional.empty());
        when(outbox.findByCorrelationIdAndEventType("tx-2", "EstoqueIndisponivel")).thenReturn(Optional.empty());
        // SKU-A tem estoque de sobra, SKU-B não tem o suficiente -- "tudo ou nada"
        when(stockItems.findById("SKU-A")).thenReturn(Optional.of(new StockItem("SKU-A", 100)));
        when(stockItems.findById("SKU-B")).thenReturn(Optional.of(new StockItem("SKU-B", 1)));

        var result = service.reserveStock("tx-2", "order-2",
                List.of(new ItemRequest("SKU-A", 5), new ItemRequest("SKU-B", 10)));

        assertEquals(InventoryReservationService.ReserveResult.INSUFFICIENT_STOCK, result);

        // NENHUM item deve ter sido decrementado -- nem SKU-A, que sozinho tinha estoque
        verify(stockItems, never()).save(any());

        ArgumentCaptor<InventoryOutboxEvent> eventCaptor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(outbox).save(eventCaptor.capture());
        assertEquals("EstoqueIndisponivel", eventCaptor.getValue().getEventType());
    }

    @Test
    void treatsMissingSkuAsZeroStock() {
        when(outbox.findByCorrelationIdAndEventType("tx-3", "EstoqueReservado")).thenReturn(Optional.empty());
        when(outbox.findByCorrelationIdAndEventType("tx-3", "EstoqueIndisponivel")).thenReturn(Optional.empty());
        when(stockItems.findById("SKU-NUNCA-CADASTRADO")).thenReturn(Optional.empty());

        var result = service.reserveStock("tx-3", "order-3",
                List.of(new ItemRequest("SKU-NUNCA-CADASTRADO", 1)));

        assertEquals(InventoryReservationService.ReserveResult.INSUFFICIENT_STOCK, result);
    }

    @Test
    void skipsAllWorkWhenAlreadyReserved() {
        when(outbox.findByCorrelationIdAndEventType("tx-4", "EstoqueReservado"))
                .thenReturn(Optional.of(new InventoryOutboxEvent("tx-4", "EstoqueReservado", "{}")));

        var result = service.reserveStock("tx-4", "order-4", List.of(new ItemRequest("SKU-A", 1)));

        assertEquals(InventoryReservationService.ReserveResult.ALREADY_PROCESSED, result);
        verify(stockItems, never()).findById(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void skipsAllWorkWhenAlreadyMarkedInsufficient() {
        when(outbox.findByCorrelationIdAndEventType("tx-5", "EstoqueReservado")).thenReturn(Optional.empty());
        when(outbox.findByCorrelationIdAndEventType("tx-5", "EstoqueIndisponivel"))
                .thenReturn(Optional.of(new InventoryOutboxEvent("tx-5", "EstoqueIndisponivel", "{}")));

        var result = service.reserveStock("tx-5", "order-5", List.of(new ItemRequest("SKU-A", 1)));

        assertEquals(InventoryReservationService.ReserveResult.ALREADY_PROCESSED, result);
        verify(stockItems, never()).findById(any());
    }

    @Test
    void releaseIncrementsStockBackAndRecordsOutboxEvent() {
        when(outbox.findByCorrelationIdAndEventType("tx-6", "EstoqueLiberado")).thenReturn(Optional.empty());
        when(stockItems.findById("SKU-A")).thenReturn(Optional.of(new StockItem("SKU-A", 2)));

        service.releaseStock("tx-6", "order-6", List.of(new ItemRequest("SKU-A", 3)));

        ArgumentCaptor<StockItem> captor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItems).save(captor.capture());
        assertEquals(5L, captor.getValue().getAvailableQuantity());

        ArgumentCaptor<InventoryOutboxEvent> eventCaptor = ArgumentCaptor.forClass(InventoryOutboxEvent.class);
        verify(outbox).save(eventCaptor.capture());
        assertEquals("EstoqueLiberado", eventCaptor.getValue().getEventType());
    }

    @Test
    void releaseIsIdempotentWhenAlreadyReleased() {
        when(outbox.findByCorrelationIdAndEventType("tx-7", "EstoqueLiberado"))
                .thenReturn(Optional.of(new InventoryOutboxEvent("tx-7", "EstoqueLiberado", "{}")));

        service.releaseStock("tx-7", "order-7", List.of(new ItemRequest("SKU-A", 1)));

        verify(stockItems, never()).save(any());
        verify(outbox, never()).save(any());
    }
}
