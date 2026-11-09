package com.example.order;

import com.example.order.client.AuthServiceClient;
import com.example.order.client.ProductServiceClient;
import com.example.order.dto.*;
import com.example.order.exception.InsufficientStockException;
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
class OrderServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthServiceClient authServiceClient;

    @MockBean
    private ProductServiceClient productServiceClient;

    private static final String TEST_TOKEN = "valid-mock-jwt-token";

    @BeforeEach
    void setUp() {
        // Default mock for AuthServiceClient
        when(authServiceClient.validateToken(eq(TEST_TOKEN)))
                .thenReturn(TokenValidationResponse.builder()
                        .valid(true)
                        .username("admin")
                        .roles(List.of("ROLE_ADMIN"))
                        .message("Token is valid")
                        .build());
    }

    @Test
    void testUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Bearer token is required")));
    }

    @Test
    void testCreateOrderSuccess() throws Exception {
        // Mock product service
        ProductDto mockProduct = ProductDto.builder()
                .id(1L)
                .name("Laptop Dell XPS 15")
                .price(new BigDecimal("1500.00"))
                .stockQuantity(10)
                .category("Laptops")
                .build();

        when(productServiceClient.getProduct(eq(1L))).thenReturn(mockProduct);
        doNothing().when(productServiceClient).deductStock(eq(1L), eq(2));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerName("Nguyen Van A")
                .customerEmail("anguyen@example.com")
                .items(List.of(new CreateOrderItemRequest(1L, 2)))
                .build();

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.customerName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.totalAmount").value(3000.00))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].productName").value("Laptop Dell XPS 15"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));

        verify(productServiceClient, times(1)).deductStock(eq(1L), eq(2));
    }

    @Test
    void testCreateOrderInsufficientStock() throws Exception {
        ProductDto outOfStockProduct = ProductDto.builder()
                .id(5L)
                .name("Dell UltraSharp 27 Monitor 4K")
                .price(new BigDecimal("450.00"))
                .stockQuantity(0)
                .category("Monitors")
                .build();

        when(productServiceClient.getProduct(eq(5L))).thenReturn(outOfStockProduct);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerName("Tran Van B")
                .customerEmail("btran@example.com")
                .items(List.of(new CreateOrderItemRequest(5L, 1)))
                .build();

        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only has 0 items in stock")));

        verify(productServiceClient, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    void testGetOrdersAndOrderLifecycle() throws Exception {
        // Setup initial product
        ProductDto mockProduct = ProductDto.builder()
                .id(2L)
                .name("iPhone 15 Pro Max")
                .price(new BigDecimal("1200.00"))
                .stockQuantity(5)
                .category("Smartphones")
                .build();

        when(productServiceClient.getProduct(eq(2L))).thenReturn(mockProduct);
        doNothing().when(productServiceClient).deductStock(eq(2L), eq(1));
        doNothing().when(productServiceClient).restoreStock(eq(2L), eq(1));

        CreateOrderRequest createReq = CreateOrderRequest.builder()
                .customerName("Le Thi C")
                .customerEmail("cle@example.com")
                .items(List.of(new CreateOrderItemRequest(2L, 1)))
                .build();

        String createRes = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(createRes).path("data").path("id").asLong();

        // 1. Get by ID
        mockMvc.perform(get("/orders/" + orderId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.customerName").value("Le Thi C"));

        // 2. Update status to completed
        UpdateOrderStatusRequest updateReq = new UpdateOrderStatusRequest("completed");
        mockMvc.perform(put("/orders/" + orderId)
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("completed"));

        // 3. Delete order
        mockMvc.perform(delete("/orders/" + orderId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk());

        // 4. Verify deleted
        mockMvc.perform(get("/orders/" + orderId)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isNotFound());
    }
}

