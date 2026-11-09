package com.example.product.controller;

import com.example.product.dto.ApiResponse;
import com.example.product.dto.ProductDto;
import com.example.product.dto.StockUpdateRequest;
import com.example.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Product Management", description = "Endpoints for managing products and stock inventory")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    @Operation(summary = "Get list of all products")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getAllProducts() {
        List<ProductDto> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success(products, "Fetched all products successfully"));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get product details by ID (Used by Order Service)")
    public ResponseEntity<ApiResponse<ProductDto>> getProductById(@PathVariable Long id) {
        ProductDto product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product, "Product details retrieved successfully"));
    }

    @PutMapping("/products/{id}/deduct-stock")
    @Operation(summary = "Deduct product inventory stock (Used during Order creation)")
    public ResponseEntity<ApiResponse<ProductDto>> deductStock(
            @PathVariable Long id,
            @RequestBody(required = false) StockUpdateRequest request,
            @RequestParam(name = "quantity", required = false) Integer queryQuantity) {

        int quantity = (request != null && request.getQuantity() != null)
                ? request.getQuantity()
                : (queryQuantity != null ? queryQuantity : 1);

        ProductDto updated = productService.deductStock(id, quantity);
        return ResponseEntity.ok(ApiResponse.success(updated, "Stock deducted successfully"));
    }

    @PutMapping("/products/{id}/restore-stock")
    @Operation(summary = "Restore product inventory stock (Used during Order cancellation / item removal)")
    public ResponseEntity<ApiResponse<ProductDto>> restoreStock(
            @PathVariable Long id,
            @RequestBody(required = false) StockUpdateRequest request,
            @RequestParam(name = "quantity", required = false) Integer queryQuantity) {

        int quantity = (request != null && request.getQuantity() != null)
                ? request.getQuantity()
                : (queryQuantity != null ? queryQuantity : 1);

        ProductDto updated = productService.restoreStock(id, quantity);
        return ResponseEntity.ok(ApiResponse.success(updated, "Stock restored successfully"));
    }

    @PostMapping("/products")
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody ProductDto dto) {
        ProductDto created = productService.createProduct(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Product created successfully"));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Update an existing product")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDto dto) {
        ProductDto updated = productService.updateProduct(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Product updated successfully"));
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "Delete a product by ID")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted successfully"));
    }

    @GetMapping("/products/health")
    @Operation(summary = "Product Service Health Check")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP", "service", "product-service", "port", "8082"), "Product service is running"));
    }
}

