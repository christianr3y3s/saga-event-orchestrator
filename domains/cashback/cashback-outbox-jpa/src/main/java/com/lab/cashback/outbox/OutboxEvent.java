package com.lab.cashback.outbox;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A constraint UNIQUE em correlationId é a garantia REAL de idempotência sob
 * concorrência -- não a checagem "já existe?" feita antes do insert (essa é só uma
 * otimização para não fazer trabalho à toa no caso comum de redelivery; sozinha ela tem
 * uma janela de corrida entre o "existe?" e o "insere").
 */
@Entity
@Table(name = "outbox_events", uniqueConstraints = @UniqueConstraint(columnNames = "correlationId"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String correlationId;

    @Column(nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected OutboxEvent() { } // JPA

    public OutboxEvent(String correlationId, String eventType, String payload) {
        this.correlationId = correlationId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
