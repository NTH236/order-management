package com.example.product.config;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    public DataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            List<Product> initialProducts = List.of(
                    Product.builder()
                            .name("Laptop Dell XPS 15")
                            .description("High-performance laptop with 16GB RAM, 512GB SSD, Intel Core i7")
                            .price(new BigDecimal("1500.00"))
                            .stockQuantity(10)
                            .category("Laptops")
                            .build(),
                    Product.builder()
                            .name("iPhone 15 Pro Max")
                            .description("Titanium design, A17 Pro chip, 256GB storage")
                            .price(new BigDecimal("1200.00"))
                            .stockQuantity(5)
                            .category("Smartphones")
                            .build(),
                    Product.builder()
                            .name("Mechanical Keyboard Keychron Q1")
                            .description("Custom mechanical keyboard with hot-swappable Gateron Pro switches")
                            .price(new BigDecimal("180.00"))
                            .stockQuantity(25)
                            .category("Accessories")
                            .build(),
                    Product.builder()
                            .name("Wireless Mouse Logitech MX Master 3S")
                            .description("Ergonomic performance wireless mouse with 8K DPI sensor")
                            .price(new BigDecimal("99.00"))
                            .stockQuantity(3)
                            .category("Accessories")
                            .build(),
                    Product.builder()
                            .name("Dell UltraSharp 27 Monitor 4K")
                            .description("27-inch 4K UHD USB-C Hub Monitor (U2723QE)")
                            .price(new BigDecimal("450.00"))
                            .stockQuantity(0) // Out of stock for testing insufficient stock scenarios
                            .category("Monitors")
                            .build()
            );

            productRepository.saveAll(initialProducts);
        }
    }
}

