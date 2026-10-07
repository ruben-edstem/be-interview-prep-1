package com.mock.taskmanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.repository.OrderRepository;
import com.mock.taskmanager.repository.ProductRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderStockCacheTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Product product;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.delete(product);
    }

    @Test
    void aCachedProductShowsTheStockLeftAfterAnOrderAndAfterItIsCancelled() throws Exception {
        product = productRepository.save(Product.builder()
                .name("Widget")
                .category("Test")
                .price(1_000)
                .stock(10)
                .rating(4.0)
                .createdAt(Instant.now())
                .build());
        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(jsonPath("$.data.stock").value(10));
        String body = """
                {"items": [{"productId": "%s", "quantity": 4}]}
                """.formatted(product.getId());

        String placed = mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "cache-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(jsonPath("$.data.stock").value(6));
        String orderId = JsonPath.read(placed, "$.data.id");
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(jsonPath("$.data.stock").value(10));
    }

    @Test
    void aCachedProductKeepsItsStockWhenTheOrderIsRefused() throws Exception {
        product = productRepository.save(Product.builder()
                .name("Widget")
                .category("Test")
                .price(1_000)
                .stock(2)
                .rating(4.0)
                .createdAt(Instant.now())
                .build());
        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(jsonPath("$.data.stock").value(2));
        String body = """
                {"items": [{"productId": "%s", "quantity": 3}]}
                """.formatted(product.getId());

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "refused-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(jsonPath("$.data.stock").value(2));
    }
}
