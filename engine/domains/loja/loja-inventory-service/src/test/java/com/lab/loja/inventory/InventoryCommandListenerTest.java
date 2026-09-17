package com.lab.loja.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryCommandListenerTest {

    @Mock ObjectMapper mapper;
    @Mock InventoryReservationService service;
    @Mock Acknowledgment ack;

    InventoryCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryCommandListener(mapper, service);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("loja.estoque.reservar", 0, 0L, "key", json);
    }

    private CommandEnvelope envelope(String correlationId, String orderId, ItemQuantityDto... items) {
        ReservationCommandData data = new ReservationCommandData();
        data.setOrderId(orderId);
        data.setItems(List.of(items));

        CommandEnvelope cmd = new CommandEnvelope();
        cmd.setType("ReservarEstoque");
        cmd.setCorrelationId(correlationId);
        cmd.setTs(System.currentTimeMillis());
        cmd.setData(data);
        return cmd;
    }

    private ItemQuantityDto item(String sku, long qty) {
        ItemQuantityDto dto = new ItemQuantityDto();
        dto.setSku(sku);
        dto.setQuantity(qty);
        return dto;
    }

    @Test
    void reservar_happyPath_acksAfterServiceCall() throws Exception {
        when(mapper.readValue(anyString(), eq(CommandEnvelope.class)))
                .thenReturn(envelope("tx-1", "order-1", item("SKU-A", 2)));
        when(service.reserveStock(eq("tx-1"), eq("order-1"), anyList()))
                .thenReturn(InventoryReservationService.ReserveResult.RESERVED);

        listener.onReservarEstoque(record("{...}"), ack);

        verify(service).reserveStock(eq("tx-1"), eq("order-1"), anyList());
        verify(ack).acknowledge();
    }

    @Test
    void reservar_malformedMessage_doesNotAckSoItGetsRedelivered() throws Exception {
        JsonProcessingException broken = mock(JsonProcessingException.class);
        when(mapper.readValue(anyString(), eq(CommandEnvelope.class))).thenThrow(broken);

        listener.onReservarEstoque(record("not-json"), ack);

        verify(ack, never()).acknowledge();
        verifyNoInteractions(service);
    }

    @Test
    void reservar_serviceThrows_doesNotAckSoItGetsRedelivered() throws Exception {
        when(mapper.readValue(anyString(), eq(CommandEnvelope.class)))
                .thenReturn(envelope("tx-2", "order-2", item("SKU-A", 1)));
        when(service.reserveStock(anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("db down"));

        listener.onReservarEstoque(record("{...}"), ack);

        verify(ack, never()).acknowledge();
    }

    @Test
    void liberar_happyPath_acksAfterServiceCall() throws Exception {
        when(mapper.readValue(anyString(), eq(CommandEnvelope.class)))
                .thenReturn(envelope("tx-3", "order-3", item("SKU-A", 1)));

        listener.onLiberarEstoque(record("{...}"), ack);

        verify(service).releaseStock(eq("tx-3"), eq("order-3"), anyList());
        verify(ack).acknowledge();
    }

    @Test
    void liberar_serviceThrows_doesNotAckSoItGetsRedelivered() throws Exception {
        when(mapper.readValue(anyString(), eq(CommandEnvelope.class)))
                .thenReturn(envelope("tx-4", "order-4", item("SKU-A", 1)));
        doThrow(new RuntimeException("db down"))
                .when(service).releaseStock(anyString(), anyString(), anyList());

        listener.onLiberarEstoque(record("{...}"), ack);

        verify(ack, never()).acknowledge();
    }
}
