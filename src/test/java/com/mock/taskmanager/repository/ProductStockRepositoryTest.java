package com.mock.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mock.taskmanager.entity.Product;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class ProductStockRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void reserveStockDeductsTheQuantityWhenEnoughIsAvailable() {
        Product product = productRepository.saveAndFlush(product(10));

        int updated = productRepository.reserveStock(product.getId(), 4);

        assertThat(updated).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(6);
    }

    @Test
    void reserveStockCanTakeTheLastUnits() {
        Product product = productRepository.saveAndFlush(product(3));

        int updated = productRepository.reserveStock(product.getId(), 3);

        assertThat(updated).isEqualTo(1);
        assertThat(stockOf(product)).isZero();
    }

    @Test
    void reserveStockChangesNothingWhenStockIsShort() {
        Product product = productRepository.saveAndFlush(product(2));

        int updated = productRepository.reserveStock(product.getId(), 3);

        assertThat(updated).isZero();
        assertThat(stockOf(product)).isEqualTo(2);
    }

    @Test
    void reserveStockForAnUnknownProductUpdatesNothing() {
        int updated = productRepository.reserveStock(UUID.randomUUID(), 1);

        assertThat(updated).isZero();
    }

    @Test
    void releaseStockAddsTheQuantityBack() {
        Product product = productRepository.saveAndFlush(product(2));

        int updated = productRepository.releaseStock(product.getId(), 5);

        assertThat(updated).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(7);
    }

    private long stockOf(Product product) {
        entityManager.clear();
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private Product product(int stock) {
        return Product.builder()
                .name("Widget")
                .category("Test")
                .price(1_000)
                .stock(stock)
                .rating(4.0)
                .createdAt(Instant.now())
                .build();
    }
}
