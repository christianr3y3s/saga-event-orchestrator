package com.lab.cashback.model;

/**
 * Espelha os campos de ValidatedTransactionEvent (do transaction-ingest-service), que é
 * o JSON que o orquestrador coloca em "data" ao emitir o comando CalculateCashback.
 */
public class TransactionData {
    private String userId;
    private long amountCents;
    private String currency;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
