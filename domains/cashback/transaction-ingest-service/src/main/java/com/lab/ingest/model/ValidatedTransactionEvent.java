package com.lab.ingest.model;

/**
 * Evento "limpo" que o orquestrador de saga (Rust) consome. Os nomes de campo seguem
 * as convenções padrão do orchestrator.properties: event.type.path=$.type e
 * correlation.path=$.correlationId.
 */
public class ValidatedTransactionEvent {

    private String type = "TransactionValidated";
    private String correlationId;
    private String userId;
    private long amountCents;
    private String currency;
    private long validatedAtMs;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public long getValidatedAtMs() { return validatedAtMs; }
    public void setValidatedAtMs(long validatedAtMs) { this.validatedAtMs = validatedAtMs; }
}
