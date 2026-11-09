package com.example.order.controller;

import com.example.order.dto.AddOrderItemRequest;
import com.example.order.dto.ApiResponse;
import com.example.order.dto.OrderItemResponse;
import com.example.order.dto.UpdateOrderItemRequest;
import com.example.order.service.OrderItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order_items")
@Tag(name = "Order Items Management", description = "Endpoints for managing individual order items")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderItemController {

    private final OrderItemService orderItemService;

    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    @GetMapping
    @Operation(summary = "Get list of all order items across orders")
    public ResponseEntity<ApiResponse<List<OrderItemResponse>>> getAllOrderItems() {
        List<OrderItemResponse> items = orderItemService.getAllOrderItems();
        return ResponseEntity.ok(ApiResponse.success(items, "Fetched all order items successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order item details by ID")
    public ResponseEntity<ApiResponse<OrderItemResponse>> getOrderItemById(@PathVariable Long id) {
        OrderItemResponse item = orderItemService.getOrderItemById(id);
        return ResponseEntity.ok(ApiResponse.success(item, "Order item details retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Add a new item to an existing order (Deducts stock & recalculates total)")
    public ResponseEntity<ApiResponse<OrderItemResponse>> addOrderItem(@Valid @RequestBody AddOrderItemRequest request) {
        OrderItemResponse created = orderItemService.addOrderItem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Order item added successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update order item quantity (Adjusts stock & recalculates total)")
    public ResponseEntity<ApiResponse<OrderItemResponse>> updateOrderItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderItemRequest request) {
        OrderItemResponse updated = orderItemService.updateOrderItem(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Order item updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove an order item from an order (Restores stock & recalculates total)")
    public ResponseEntity<ApiResponse<Void>> deleteOrderItem(@PathVariable Long id) {
        orderItemService.deleteOrderItem(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Order item deleted successfully"));
    }
}

