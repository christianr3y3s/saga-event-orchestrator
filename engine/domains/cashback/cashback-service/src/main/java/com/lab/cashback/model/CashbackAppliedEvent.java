package com.lab.cashback.model;

public class CashbackAppliedEvent {
    private String type = "CashbackApplied";
    private String correlationId;
    private String userId;
    private long cashbackCents;
    private long appliedAtMs;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getCashbackCents() { return cashbackCents; }
    public void setCashbackCents(long cashbackCents) { this.cashbackCents = cashbackCents; }

    public long getAppliedAtMs() { return appliedAtMs; }
    public void setAppliedAtMs(long appliedAtMs) { this.appliedAtMs = appliedAtMs; }
}
