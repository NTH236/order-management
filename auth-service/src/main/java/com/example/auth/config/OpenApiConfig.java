package com.example.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API - SOA Architecture (TH2)")
                        .description("Authentication & JWT Token Validation Service for Order Management System")
                        .version("1.0.0")
                        .contact(new Contact().name("Lead Backend Architect").email("architect@example.com")));
    }
}

