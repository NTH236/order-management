package com.example.product;

import com.example.product.dto.ProductDto;
import com.example.product.dto.StockUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetAllProducts() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Laptop Dell XPS 15"));
    }

    @Test
    void testGetProductById() throws Exception {
        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Laptop Dell XPS 15"))
                .andExpect(jsonPath("$.data.price").value(1500.00));
    }

    @Test
    void testDeductStockSuccessAndRestore() throws Exception {
        StockUpdateRequest deductReq = new StockUpdateRequest(2);

        // Deduct 2
        mockMvc.perform(put("/products/2/deduct-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deductReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(3));

        // Restore 2
        mockMvc.perform(put("/products/2/restore-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deductReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(5));
    }

    @Test
    void testDeductStockInsufficient() throws Exception {
        // Product 5 has 0 stock
        StockUpdateRequest deductReq = new StockUpdateRequest(1);

        mockMvc.perform(put("/products/5/deduct-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deductReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only has 0 items in stock")));
    }
}

