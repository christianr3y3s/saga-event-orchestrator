package com.lab.loja.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryOutboxEventRepository extends JpaRepository<InventoryOutboxEvent, Long> {
    Optional<InventoryOutboxEvent> findByCorrelationIdAndEventType(String correlationId, String eventType);
    List<InventoryOutboxEvent> findTop50ByStatusOrderByIdAsc(OutboxStatus status);
}
