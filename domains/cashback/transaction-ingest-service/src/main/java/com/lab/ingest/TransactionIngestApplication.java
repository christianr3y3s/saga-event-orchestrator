package com.lab.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // necessário para o OutboxReconciler
public class TransactionIngestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionIngestApplication.class, args);
    }
}
