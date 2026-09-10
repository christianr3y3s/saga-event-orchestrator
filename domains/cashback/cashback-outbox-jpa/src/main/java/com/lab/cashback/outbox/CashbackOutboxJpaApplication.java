package com.lab.cashback.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CashbackOutboxJpaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CashbackOutboxJpaApplication.class, args);
    }
}
