package com.lab.cashback.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CashbackCalculator {

    private final double percentage;

    public CashbackCalculator(@Value("${app.cashback.percentage:0.01}") double percentage) {
        this.percentage = percentage;
    }

    public long calculate(long amountCents) {
        return Math.round(amountCents * percentage);
    }
}
