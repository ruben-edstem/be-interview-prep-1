package com.mock.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.mapper.ProductMapper;
import com.mock.taskmanager.repository.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, new ProductMapper());
    }

    @Test
    void createStoresTheNameAndStock() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse created = productService.create(new ProductRequest("Widget", 10L));

        assertThat(created.name()).isEqualTo("Widget");
        assertThat(created.stock()).isEqualTo(10);
    }

    @Test
    void getReturnsTheProduct() {
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(Product.builder().id(PRODUCT_ID).name("Widget").stock(3).build()));

        ProductResponse found = productService.get(PRODUCT_ID);

        assertThat(found.id()).isEqualTo(PRODUCT_ID);
        assertThat(found.stock()).isEqualTo(3);
    }

    @Test
    void getFailsForAnUnknownProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.get(PRODUCT_ID));
    }
}
