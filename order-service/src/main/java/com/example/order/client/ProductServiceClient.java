package com.example.order.client;

import com.example.order.dto.ApiResponse;
import com.example.order.dto.ProductDto;
import com.example.order.dto.StockUpdateRequest;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.ResourceNotFoundException;
import com.example.order.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductServiceClient(
            @Value("${services.product-service.url:http://localhost:8082}") String productServiceUrl,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public ProductDto getProduct(Long productId) {
        try {
            String rawJson = restClient.get()
                    .uri("/products/{id}", productId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                            throw new ResourceNotFoundException("Product not found with ID: " + productId);
                        }
                    })
                    .body(String.class);

            ApiResponse<ProductDto> apiResponse = objectMapper.readValue(rawJson, new TypeReference<>() {});
            if (apiResponse != null && apiResponse.getData() != null) {
                return apiResponse.getData();
            }
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Product Service is unreachable at /products/{}", productId, ex);
            throw new ServiceUnavailableException("Product Service is currently unavailable. Please try again later.", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        } catch (Exception ex) {
            log.error("Error communicating with Product Service for product {}: {}", productId, ex.getMessage());
            throw new ServiceUnavailableException("Error retrieving product details from Product Service: " + ex.getMessage(), ex);
        }
    }

    public void deductStock(Long productId, Integer quantity) {
        try {
            StockUpdateRequest request = new StockUpdateRequest(quantity);

            restClient.put()
                    .uri("/products/{id}/deduct-stock", productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        byte[] body = resp.getBody().readAllBytes();
                        String errorMsg = "Insufficient stock for product ID: " + productId;
                        try {
                            JsonNode node = objectMapper.readTree(body);
                            if (node.has("message")) {
                                errorMsg = node.get("message").asText();
                            }
                        } catch (Exception ignored) {}
                        throw new InsufficientStockException(errorMsg);
                    })
                    .toBodilessEntity();

        } catch (InsufficientStockException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Product Service is unreachable during stock deduction for product {}: {}", productId, ex.getMessage());
            throw new ServiceUnavailableException("Product Service is currently unavailable to deduct stock.", ex);
        } catch (Exception ex) {
            if (ex instanceof InsufficientStockException ise) {
                throw ise;
            }
            log.error("Failed to deduct stock for product {}: {}", productId, ex.getMessage());
            throw new ServiceUnavailableException("Stock deduction failed: " + ex.getMessage(), ex);
        }
    }

    public void restoreStock(Long productId, Integer quantity) {
        try {
            StockUpdateRequest request = new StockUpdateRequest(quantity);

            restClient.put()
                    .uri("/products/{id}/restore-stock", productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (ResourceAccessException ex) {
            log.error("Product Service is unreachable during stock restoration for product {}: {}", productId, ex.getMessage());
            // In SOA architecture, stock restoration should log a compensating transaction error
        } catch (Exception ex) {
            log.error("Failed to restore stock for product {}: {}", productId, ex.getMessage());
        }
    }
}

