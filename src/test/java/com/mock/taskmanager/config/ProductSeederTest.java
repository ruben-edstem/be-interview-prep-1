package com.mock.taskmanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ProductSeederTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationArguments arguments;

    private ProductSeeder productSeeder;

    @BeforeEach
    void setUp() {
        productSeeder = new ProductSeeder(productRepository);
    }

    @Test
    void anEmptyCatalogIsSeededWithTheConfiguredNumberOfProducts() {
        when(productRepository.count()).thenReturn(0L);

        productSeeder.run(arguments);

        List<Product> seeded = savedProducts();
        assertThat(seeded).hasSize(ProductSeeder.SEED_COUNT);
    }

    @Test
    void seededProductsHaveValidFieldsAndAMixOfStockLevels() {
        when(productRepository.count()).thenReturn(0L);

        productSeeder.run(arguments);

        List<Product> seeded = savedProducts();
        assertThat(seeded).allSatisfy(product -> {
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getCategory()).isNotBlank();
            assertThat(product.getPrice()).isPositive();
            assertThat(product.getStock()).isNotNegative();
            assertThat(product.getRating()).isBetween(1.0, 5.0);
            assertThat(product.getCreatedAt()).isNotNull();
        });
        assertThat(seeded.stream().map(Product::getCategory).distinct()).hasSizeGreaterThan(1);
        assertThat(seeded).anyMatch(product -> product.getStock() == 0);
        assertThat(seeded).anyMatch(product -> product.getStock() > 0);
    }

    @Test
    void aCatalogThatAlreadyHasProductsIsLeftAlone() {
        when(productRepository.count()).thenReturn(7L);

        productSeeder.run(arguments);

        verify(productRepository, never()).saveAll(anyList());
    }

    private List<Product> savedProducts() {
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.captor();
        verify(productRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
