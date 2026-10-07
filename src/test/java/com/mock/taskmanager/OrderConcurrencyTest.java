package com.mock.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mock.taskmanager.entity.CustomerOrder;
import com.mock.taskmanager.entity.OrderStatus;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.repository.OrderRepository;
import com.mock.taskmanager.repository.ProductRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OrderConcurrencyTest {

    private static final int REQUESTS = 50;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private final List<Product> createdProducts = new ArrayList<>();

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        executor = Executors.newFixedThreadPool(REQUESTS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        orderRepository.deleteAll();
        productRepository.deleteAll(createdProducts);
        createdProducts.clear();
    }

    @Test
    void fiftySimultaneousOrdersForTenUnitsOfStockSucceedExactlyTenTimes() throws Exception {
        Product product = createProduct("Widget", 10);

        List<Integer> statuses = fireSimultaneously(i -> placeOrder("order-" + i, product.getId(), 1));

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(10);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(REQUESTS - 10);
        assertThat(stockOf(product)).isZero();
        assertThat(orderRepository.count()).isEqualTo(10);
    }

    @Test
    void simultaneousMultiItemOrdersNeverOversellEitherProduct() throws Exception {
        Product first = createProduct("First", 10);
        Product second = createProduct("Second", 4);

        List<Integer> statuses = fireSimultaneously(i -> placeOrder("multi-" + i, i % 2 == 0
                ? List.of(first.getId(), second.getId())
                : List.of(second.getId(), first.getId())));

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(4);
        assertThat(stockOf(first)).isEqualTo(6);
        assertThat(stockOf(second)).isZero();
        assertThat(orderRepository.count()).isEqualTo(4);
    }

    @Test
    void fiftySimultaneousRetriesOfOneRequestCreateASingleOrder() throws Exception {
        Product product = createProduct("Widget", 10);

        List<Integer> statuses = fireSimultaneously(i -> placeOrder("retried-key", product.getId(), 3));

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(REQUESTS - 1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(7);
    }

    @Test
    void simultaneousCancelsOfOneOrderReturnTheStockOnce() throws Exception {
        Product product = createProduct("Widget", 10);
        placeOrder("to-cancel", product.getId(), 4);
        CustomerOrder order = orderRepository.findByIdempotencyKey("to-cancel").orElseThrow();

        List<Integer> statuses = fireSimultaneously(i -> cancel(order.getId()));

        assertThat(statuses).containsOnly(200);
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    private List<Integer> fireSimultaneously(IntFunction<Integer> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            int index = i;
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return request.apply(index);
            };
            futures.add(executor.submit(task));
        }
        ready.await();
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        return statuses;
    }

    private int placeOrder(String key, UUID productId, long quantity) {
        String body = """
                {"items": [{"productId": "%s", "quantity": %d}]}
                """.formatted(productId, quantity);
        return send(key, body);
    }

    private int placeOrder(String key, List<UUID> productIds) {
        String items = productIds.stream()
                .map(id -> """
                        {"productId": "%s", "quantity": 1}""".formatted(id))
                .reduce((a, b) -> a + "," + b)
                .orElseThrow();
        return send(key, "{\"items\": [" + items + "]}");
    }

    private int send(String key, String body) {
        try {
            return mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int cancel(UUID orderId) {
        try {
            return mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Product createProduct(String name, int stock) {
        Product product = productRepository.save(Product.builder()
                .name(name)
                .category("Test")
                .price(1_000)
                .stock(stock)
                .rating(4.0)
                .createdAt(Instant.now())
                .build());
        createdProducts.add(product);
        return product;
    }

    private long stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }
}
