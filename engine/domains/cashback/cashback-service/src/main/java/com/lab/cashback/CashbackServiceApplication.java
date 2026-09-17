package com.lab.cashback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // necessário para o CashbackOutboxReconciler
public class CashbackServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CashbackServiceApplication.class, args);
    }
}
