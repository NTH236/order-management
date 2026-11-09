package com.example.order.client;

import com.example.order.dto.ApiResponse;
import com.example.order.dto.TokenValidationResponse;
import com.example.order.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AuthServiceClient(
            @Value("${services.auth-service.url:http://localhost:8081}") String authServiceUrl,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public TokenValidationResponse validateToken(String token) {
        try {
            String rawJson = restClient.get()
                    .uri("/api/auth/validate")
                    .header("Authorization", "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        log.warn("Auth Service returned 4xx: {}", response.getStatusCode());
                    })
                    .body(String.class);

            if (rawJson == null) {
                return TokenValidationResponse.builder().valid(false).message("Empty response from Auth Service").build();
            }

            ApiResponse<TokenValidationResponse> apiResponse = objectMapper.readValue(
                    rawJson,
                    new TypeReference<>() {}
            );

            if (apiResponse != null && apiResponse.getData() != null) {
                return apiResponse.getData();
            }
            return TokenValidationResponse.builder().valid(false).message("Invalid token").build();
        } catch (ResourceAccessException ex) {
            log.error("Auth Service is unreachable at endpoint /api/auth/validate", ex);
            throw new ServiceUnavailableException("Auth Service is unreachable: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.warn("Failed to validate token with Auth Service: {}", ex.getMessage());
            return TokenValidationResponse.builder().valid(false).message("Token validation failed: " + ex.getMessage()).build();
        }
    }
}

