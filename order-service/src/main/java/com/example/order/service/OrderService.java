package com.example.order.service;

import com.example.order.client.ProductServiceClient;
import com.example.order.dto.*;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.InvalidOrderStateException;
import com.example.order.exception.ResourceNotFoundException;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;

    public OrderService(OrderRepository orderRepository, ProductServiceClient productServiceClient) {
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = findOrderById(id);
        return toOrderResponse(order);
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating new order for customer: {}", request.getCustomerName());

        Order order = Order.builder()
                .customerName(request.getCustomerName().trim())
                .customerEmail(request.getCustomerEmail().trim())
                .status(OrderStatus.PENDING.getValue())
                .totalAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();

        // Step 1: Pre-check inventory availability for all items
        for (CreateOrderItemRequest itemReq : request.getItems()) {
            ProductDto product = productServiceClient.getProduct(itemReq.getProductId());
            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new InsufficientStockException(String.format(
                        "Product '%s' (ID: %d) only has %d items in stock, but %d requested",
                        product.getName(), product.getId(), product.getStockQuantity(), itemReq.getQuantity()));
            }
        }

        // Step 2: Deduct inventory and build OrderItem records
        List<OrderItem> deductedItems = new ArrayList<>();
        try {
            for (CreateOrderItemRequest itemReq : request.getItems()) {
                ProductDto product = productServiceClient.getProduct(itemReq.getProductId());

                // Deduct stock via Product Service API
                productServiceClient.deductStock(product.getId(), itemReq.getQuantity());

                BigDecimal unitPrice = product.getPrice();
                BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

                OrderItem item = OrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(unitPrice)
                        .totalPrice(totalPrice)
                        .build();

                order.addItem(item);
                deductedItems.add(item);
            }
        } catch (Exception ex) {
            // Compensating transaction in case of partial failures
            log.error("Error occurred while deducting stock during order creation. Compensating...", ex);
            for (OrderItem deducted : deductedItems) {
                try {
                    productServiceClient.restoreStock(deducted.getProductId(), deducted.getQuantity());
                } catch (Exception compEx) {
                    log.error("Failed to compensate stock for product ID {}: {}", deducted.getProductId(), compEx.getMessage());
                }
            }
            throw ex;
        }

        order.recalculateTotal();
        Order savedOrder = orderRepository.save(order);
        log.info("Order #{} successfully created with total amount: {}", savedOrder.getId(), savedOrder.getTotalAmount());
        return toOrderResponse(savedOrder);
    }

    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = findOrderById(id);
        OrderStatus newStatus = OrderStatus.fromString(request.getStatus());
        OrderStatus currentStatus = OrderStatus.fromString(order.getStatus());

        if (currentStatus == newStatus) {
            return toOrderResponse(order);
        }

        if (currentStatus == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Cannot update status of an already cancelled order.");
        }

        if (currentStatus == OrderStatus.COMPLETED && newStatus != OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException("Cannot modify status of a completed order.");
        }

        // If transitioning from PENDING to CANCELLED: restore product stocks
        if (newStatus == OrderStatus.CANCELLED && currentStatus == OrderStatus.PENDING) {
            log.info("Cancelling order #{}. Restoring stock for {} item(s)...", order.getId(), order.getItems().size());
            for (OrderItem item : order.getItems()) {
                productServiceClient.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(newStatus.getValue());
        Order updated = orderRepository.save(order);
        return toOrderResponse(updated);
    }

    public void deleteOrder(Long id) {
        Order order = findOrderById(id);
        OrderStatus currentStatus = OrderStatus.fromString(order.getStatus());

        // If order is active/pending, restore inventory before deletion
        if (currentStatus == OrderStatus.PENDING) {
            log.info("Deleting pending order #{}. Restoring stock for {} item(s)...", order.getId(), order.getItems().size());
            for (OrderItem item : order.getItems()) {
                productServiceClient.restoreStock(item.getProductId(), item.getQuantity());
            }
        }

        orderRepository.delete(order);
        log.info("Order #{} and related items successfully deleted", id);
    }

    public Order findOrderById(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));
    }

    public OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = (order.getItems() == null) ? List.of() :
                order.getItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .id(item.getId())
                                .orderId(order.getId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .totalPrice(item.getTotalPrice())
                                .build())
                        .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}

