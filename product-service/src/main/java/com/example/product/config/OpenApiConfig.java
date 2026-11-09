package com.example.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product & Inventory Service API - SOA Architecture (TH3)")
                        .description("Product catalog and inventory stock management microservice")
                        .version("1.0.0")
                        .contact(new Contact().name("Lead Backend Architect").email("architect@example.com")));
    }
}

