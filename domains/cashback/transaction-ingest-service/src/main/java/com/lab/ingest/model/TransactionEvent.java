package com.lab.ingest.model;

/**
 * Mensagem crua publicada pelo terminal de pagamento (POS) na compra no crédito.
 * O terminal publica isso e segue em frente -- não espera o cashback ser calculado.
 */
public class TransactionEvent {

    private String transactionId;
    private String userId;
    private long amountCents;
    private String currency;
    private long createdAtMs;

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
