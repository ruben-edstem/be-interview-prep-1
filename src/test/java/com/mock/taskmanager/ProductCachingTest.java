package com.mock.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.repository.ProductRepository;
import com.mock.taskmanager.service.ProductService;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest
class ProductCachingTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<UUID> createdIds = new ArrayList<>();

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(cacheManager.getCache(ProductService.PRODUCT_CACHE)).clear();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @AfterEach
    void removeCreatedProducts() {
        productRepository.deleteAllById(createdIds);
        createdIds.clear();
    }

    @Test
    void repeatedLookupsOfTheSameProductRunOneSelectStatement() {
        UUID id = create("Lamp", 2_500);
        statistics.clear();

        for (int lookup = 0; lookup < 50; lookup++) {
            productService.get(id);
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void aCachedLookupReturnsTheSameDataAsTheFirstOne() {
        UUID id = create("Lamp", 2_500);

        ProductResponse first = productService.get(id);
        ProductResponse second = productService.get(id);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void lookupsOfDifferentProductsAreCachedSeparately() {
        UUID lamp = create("Lamp", 2_500);
        UUID desk = create("Desk", 9_900);
        statistics.clear();

        productService.get(lamp);
        productService.get(desk);
        productService.get(lamp);
        productService.get(desk);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void anUpdatedProductIsNeverReturnedStale() {
        UUID id = create("Lamp", 2_500);
        productService.get(id);
        statistics.clear();

        productService.update(id, request("Desk Lamp", 3_100));
        ProductResponse afterUpdate = productService.get(id);
        long statementsAfterUpdate = statistics.getPrepareStatementCount();
        productService.get(id);

        assertThat(afterUpdate.name()).isEqualTo("Desk Lamp");
        assertThat(afterUpdate.price()).isEqualTo(3_100);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(statementsAfterUpdate);
    }

    @Test
    void aDeletedProductIsNeverReturnedFromTheCache() {
        UUID id = create("Lamp", 2_500);
        productService.get(id);

        productService.delete(id);

        assertThrows(ProductNotFoundException.class, () -> productService.get(id));
    }

    @Test
    void aFailedLookupIsNotCached() {
        UUID id = UUID.randomUUID();

        assertThrows(ProductNotFoundException.class, () -> productService.get(id));
        UUID created = create("Lamp", 2_500);

        assertThat(productService.get(created).name()).isEqualTo("Lamp");
    }

    private UUID create(String name, long price) {
        UUID id = productService.create(request(name, price)).id();
        createdIds.add(id);
        return id;
    }

    private ProductRequest request(String name, long price) {
        return new ProductRequest(name, "Home", price, 5, 4.5);
    }
}
