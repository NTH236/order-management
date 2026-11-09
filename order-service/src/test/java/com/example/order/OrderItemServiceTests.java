package com.example.order;

import com.example.order.client.AuthServiceClient;
import com.example.order.client.ProductServiceClient;
import com.example.order.dto.*;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderItemServiceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @MockBean
    private AuthServiceClient authServiceClient;

    @MockBean
    private ProductServiceClient productServiceClient;

    private static final String TEST_TOKEN = "valid-mock-jwt-token";
    private Order existingOrder;

    @BeforeEach
    void setUp() {
        when(authServiceClient.validateToken(eq(TEST_TOKEN)))
                .thenReturn(TokenValidationResponse.builder()
                        .valid(true)
                        .username("admin")
                        .roles(List.of("ROLE_ADMIN"))
                        .message("Token is valid")
                        .build());

        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        // Seed a test order
        Order order = Order.builder()
                .customerName("Pham Van D")
                .customerEmail("dpham@example.com")
                .status("pending")
                .totalAmount(new BigDecimal("180.00"))
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .productId(3L)
                .productName("Mechanical Keyboard Keychron Q1")
                .quantity(1)
                .unitPrice(new BigDecimal("180.00"))
                .totalPrice(new BigDecimal("180.00"))
                .build();

        order.addItem(item);
        existingOrder = orderRepository.save(order);
    }

    @Test
    void testGetAllOrderItems() throws Exception {
        mockMvc.perform(get("/order_items")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productName").value("Mechanical Keyboard Keychron Q1"));
    }

    @Test
    void testAddOrderItemToExistingOrder() throws Exception {
        ProductDto mockMouse = ProductDto.builder()
                .id(4L)
                .name("Wireless Mouse Logitech MX Master 3S")
                .price(new BigDecimal("99.00"))
                .stockQuantity(3)
                .category("Accessories")
                .build();

        when(productServiceClient.getProduct(eq(4L))).thenReturn(mockMouse);
        doNothing().when(productServiceClient).deductStock(eq(4L), eq(1));

        AddOrderItemRequest addReq = AddOrderItemRequest.builder()
                .orderId(existingOrder.getId())
                .productId(4L)
                .quantity(1)
                .build();

        mockMvc.perform(post("/order_items")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.productName").value("Wireless Mouse Logitech MX Master 3S"))
                .andExpect(jsonPath("$.data.totalPrice").value(99.00));

        // Verify order total was updated: 180 + 99 = 279
        Order updatedOrder = orderRepository.findByIdWithItems(existingOrder.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("279.00"), updatedOrder.getTotalAmount());
    }

    @Test
    void testUpdateOrderItemQuantity() throws Exception {
        OrderItem item = existingOrder.getItems().get(0);

        ProductDto mockKeyboard = ProductDto.builder()
                .id(3L)
                .name("Mechanical Keyboard Keychron Q1")
                .price(new BigDecimal("180.00"))
                .stockQuantity(24)
                .category("Accessories")
                .build();

        when(productServiceClient.getProduct(eq(3L))).thenReturn(mockKeyboard);
        doNothing().when(productServiceClient).deductStock(eq(3L), eq(1)); // 2 - 1 = 1 extra

        UpdateOrderItemRequest updateReq = new UpdateOrderItemRequest(2);

        mockMvc.perform(put("/order_items/" + item.getId())
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(360.00));

        // Verify order total updated: 360
        Order updatedOrder = orderRepository.findByIdWithItems(existingOrder.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("360.00"), updatedOrder.getTotalAmount());
    }

    @Test
    void testDeleteOrderItem() throws Exception {
        OrderItem item = existingOrder.getItems().get(0);
        doNothing().when(productServiceClient).restoreStock(eq(3L), eq(1));

        mockMvc.perform(delete("/order_items/" + item.getId())
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        Order updatedOrder = orderRepository.findByIdWithItems(existingOrder.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, updatedOrder.getTotalAmount().compareTo(BigDecimal.ZERO));
        org.junit.jupiter.api.Assertions.assertEquals(0, updatedOrder.getItems().size());
        verify(productServiceClient, times(1)).restoreStock(eq(3L), eq(1));
    }
}
