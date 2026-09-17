package com.lab.cashback.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    Optional<OutboxEvent> findByCorrelationId(String correlationId);
    List<OutboxEvent> findTop50ByStatusOrderByIdAsc(OutboxStatus status);
}
