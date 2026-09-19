package com.lab.logistica.rota.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Libera CORS para os dois endpoints públicos (`/rotas/simular`, `/fretes/calcular`) --
 * necessário porque a interface (página estática hospedada separadamente, ex.: Hostinger)
 * chama esta API de uma origem diferente. Lista de origens configurável via
 * {@code app.cors.allowed-origins} (application.yml ou variável de ambiente
 * {@code APP_CORS_ALLOWED_ORIGINS}, separadas por vírgula) -- o default é {@code *} só
 * para facilitar o primeiro teste de aceitação; troque pelo domínio real da interface antes
 * de considerar isto pronto para produção (ver README).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origensPermitidas;

    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String origensPermitidasCsv) {
        this.origensPermitidas = origensPermitidasCsv.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/rotas/**")
                .allowedOrigins(origensPermitidas)
                .allowedMethods("POST");
        registry.addMapping("/fretes/**")
                .allowedOrigins(origensPermitidas)
                .allowedMethods("POST");
    }
}
