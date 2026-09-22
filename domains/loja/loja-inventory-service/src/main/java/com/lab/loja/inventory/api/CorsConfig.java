package com.lab.loja.inventory.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Libera CORS para o único endpoint público (`/estoque/**`) -- necessário porque o
 * front-end (`domains/loja/loja-inventory-web`, hospedado separadamente) chama esta API de
 * uma origem diferente. Mesmo padrão de {@code logistica-rota-service}: lista de origens
 * configurável via {@code app.cors.allowed-origins} (application.yml ou variável de
 * ambiente {@code APP_CORS_ALLOWED_ORIGINS}, separadas por vírgula); o default {@code *} é
 * só para o primeiro teste de aceitação.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origensPermitidas;

    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String origensPermitidasCsv) {
        this.origensPermitidas = origensPermitidasCsv.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/estoque/**")
                .allowedOrigins(origensPermitidas)
                .allowedMethods("GET");
    }
}
