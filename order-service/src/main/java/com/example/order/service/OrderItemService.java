package com.example.order.service;

import com.example.order.client.ProductServiceClient;
import com.example.order.dto.*;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.InvalidOrderStateException;
import com.example.order.exception.ResourceNotFoundException;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderItemService {

    private static final Logger log = LoggerFactory.getLogger(OrderItemService.class);

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;

    public OrderItemService(OrderItemRepository orderItemRepository,
                            OrderRepository orderRepository,
                            ProductServiceClient productServiceClient) {
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
    }

    @Transactional(readOnly = true)
    public List<OrderItemResponse> getAllOrderItems() {
        return orderItemRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderItemResponse getOrderItemById(Long id) {
        OrderItem item = findItemById(id);
        return toResponse(item);
    }

    public OrderItemResponse addOrderItem(AddOrderItemRequest request) {
        Order order = orderRepository.findByIdWithItems(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + request.getOrderId()));

        validateOrderModifiable(order);

        // Verify and deduct stock
        ProductDto product = productServiceClient.getProduct(request.getProductId());
        if (product.getStockQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(String.format(
                    "Product '%s' (ID: %d) only has %d items in stock, but %d requested",
                    product.getName(), product.getId(), product.getStockQuantity(), request.getQuantity()));
        }

        productServiceClient.deductStock(product.getId(), request.getQuantity());

        // Check if item already exists in the order
        Optional<OrderItem> existingItem = order.getItems().stream()
                .filter(i -> i.getProductId().equals(product.getId()))
                .findFirst();

        OrderItem targetItem;
        if (existingItem.isPresent()) {
            OrderItem item = existingItem.get();
            item.updateQuantityAndTotal(item.getQuantity() + request.getQuantity());
            targetItem = item;
        } else {
            BigDecimal unitPrice = product.getPrice();
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

            OrderItem newItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(request.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(totalPrice)
                    .build();

            order.addItem(newItem);
            targetItem = newItem;
        }

        order.recalculateTotal();
        Order savedOrder = orderRepository.saveAndFlush(order);

        // Retrieve the saved item with generated ID
        OrderItem persistedItem = savedOrder.getItems().stream()
                .filter(i -> i.getProductId().equals(product.getId()))
                .findFirst()
                .orElse(targetItem);

        log.info("Added item #{} (Product ID {}) to order #{}, updated order total: {}",
                persistedItem.getId(), product.getId(), savedOrder.getId(), savedOrder.getTotalAmount());

        return toResponse(persistedItem);
    }

    public OrderItemResponse updateOrderItem(Long id, UpdateOrderItemRequest request) {
        OrderItem item = findItemById(id);
        Order order = item.getOrder();

        validateOrderModifiable(order);

        int oldQuantity = item.getQuantity();
        int newQuantity = request.getQuantity();
        int diff = newQuantity - oldQuantity;

        if (diff > 0) {
            // Need to deduct additional stock
            ProductDto product = productServiceClient.getProduct(item.getProductId());
            if (product.getStockQuantity() < diff) {
                throw new InsufficientStockException(String.format(
                        "Cannot increase item quantity by %d: Product '%s' only has %d in stock",
                        diff, product.getName(), product.getStockQuantity()));
            }
            productServiceClient.deductStock(item.getProductId(), diff);
        } else if (diff < 0) {
            // Restore excess stock
            productServiceClient.restoreStock(item.getProductId(), Math.abs(diff));
        }

        item.updateQuantityAndTotal(newQuantity);
        order.recalculateTotal();
        orderRepository.saveAndFlush(order);
        log.info("Updated item #{} quantity to {}, updated order total: {}", id, newQuantity, order.getTotalAmount());

        return toResponse(item);
    }

    public void deleteOrderItem(Long id) {
        OrderItem item = findItemById(id);
        Order order = item.getOrder();

        validateOrderModifiable(order);

        // Restore stock to product service
        productServiceClient.restoreStock(item.getProductId(), item.getQuantity());

        order.getItems().removeIf(i -> i.getId().equals(id));
        orderItemRepository.delete(item);
        order.recalculateTotal();
        orderRepository.saveAndFlush(order);
        log.info("Deleted order item #{} from order #{}, updated order total: {}", id, order.getId(), order.getTotalAmount());
    }

    private void validateOrderModifiable(Order order) {
        OrderStatus status = OrderStatus.fromString(order.getStatus());
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Cannot modify items in a cancelled order.");
        }
        if (status == OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException("Cannot modify items in a completed order.");
        }
    }

    private OrderItem findItemById(Long id) {
        return orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with ID: " + id));
    }

    private OrderItemResponse toResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .orderId(item.getOrder() != null ? item.getOrder().getId() : null)
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}
