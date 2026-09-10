package com.lab.cashback.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "balances")
public class Balance {

    @Id
    private String userId;

    private long amountCents;

    protected Balance() { } // JPA

    public Balance(String userId, long amountCents) {
        this.userId = userId;
        this.amountCents = amountCents;
    }

    public String getUserId() { return userId; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
}
