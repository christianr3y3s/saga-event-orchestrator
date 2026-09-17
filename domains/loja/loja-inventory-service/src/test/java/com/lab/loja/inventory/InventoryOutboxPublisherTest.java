package com.lab.loja.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class InventoryOutboxPublisherTest {

    @Mock InventoryOutboxEventRepository outbox;
    @Mock KafkaTemplate<String, String> kafka;

    InventoryOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new InventoryOutboxPublisher(outbox, kafka,
                "loja.estoque.reservado", "loja.estoque.indisponivel", "loja.estoque.liberado");
    }

    @Test
    void routesEachEventTypeToItsOwnTopic() {
        InventoryOutboxEvent reservado = new InventoryOutboxEvent("tx-1", "EstoqueReservado", "{}");
        InventoryOutboxEvent indisponivel = new InventoryOutboxEvent("tx-2", "EstoqueIndisponivel", "{}");
        InventoryOutboxEvent liberado = new InventoryOutboxEvent("tx-3", "EstoqueLiberado", "{}");
        when(outbox.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(reservado, indisponivel, liberado));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publishPending();

        verify(kafka).send("loja.estoque.reservado", "tx-1", "{}");
        verify(kafka).send("loja.estoque.indisponivel", "tx-2", "{}");
        verify(kafka).send("loja.estoque.liberado", "tx-3", "{}");
        assertEquals(OutboxStatus.PUBLISHED, reservado.getStatus());
        assertEquals(OutboxStatus.PUBLISHED, indisponivel.getStatus());
        assertEquals(OutboxStatus.PUBLISHED, liberado.getStatus());
    }

    @Test
    void skipsEventWithUnknownTypeWithoutThrowing() {
        InventoryOutboxEvent unknown = new InventoryOutboxEvent("tx-4", "TipoDesconhecido", "{}");
        when(outbox.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)).thenReturn(List.of(unknown));

        assertDoesNotThrow(() -> publisher.publishPending());

        verifyNoInteractions(kafka);
        verify(outbox, never()).save(any());
    }

    @Test
    void leavesEventPendingWhenKafkaSendFails() {
        InventoryOutboxEvent failing = new InventoryOutboxEvent("tx-5", "EstoqueReservado", "{}");
        when(outbox.findTop50ByStatusOrderByIdAsc(OutboxStatus.PENDING)).thenReturn(List.of(failing));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertDoesNotThrow(() -> publisher.publishPending());

        assertEquals(OutboxStatus.PENDING, failing.getStatus());
        verify(outbox, never()).save(any());
    }
}
