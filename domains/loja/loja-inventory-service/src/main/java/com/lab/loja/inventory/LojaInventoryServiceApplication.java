package com.lab.loja.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LojaInventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LojaInventoryServiceApplication.class, args);
    }
}
