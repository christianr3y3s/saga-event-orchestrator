package com.lab.loja.inventory;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Diferença importante em relação a OutboxEvent do cashback: lá a constraint UNIQUE era
 * só em correlationId, porque cada transação tinha no máximo UM evento de saída possível
 * (CashbackApplied) durante toda a vida daquele correlationId.
 *
 * Aqui não: o MESMO correlationId (o pedido) pode gerar dois eventos de saída em momentos
 * diferentes -- "EstoqueReservado" (forward) e, se o pagamento for recusado depois,
 * "EstoqueLiberado" (compensação). Se a constraint fosse só em correlationId, o insert da
 * compensação falharia sempre (acharia que já existe um evento pra esse correlationId).
 *
 * Por isso a constraint UNIQUE aqui é composta: (correlationId, eventType). Isso ainda
 * garante a idempotência de CADA evento individualmente sob concorrência/redelivery --
 * só permite que o PAR se repita quando o eventType é diferente, que é exatamente o caso
 * de negócio válido (reserva seguida de liberação).
 */
@Entity
@Table(name = "inventory_outbox_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"correlationId", "eventType"}))
public class InventoryOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
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

    protected InventoryOutboxEvent() { } // JPA

    public InventoryOutboxEvent(String correlationId, String eventType, String payload) {
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
