package com.mock.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mock.taskmanager.dto.request.ProductFilter;
import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.mapper.ProductMapper;
import com.mock.taskmanager.repository.ProductRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final ProductFilter NO_FILTER = new ProductFilter(null, null, null, null, null);

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, new ProductMapper());
    }

    @Test
    void createStoresTheRequestedFieldsAndSetsTheCreatedDate() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductRequest request = new ProductRequest("Lamp", "Home", 2_500L, 5, 4.5);

        ProductResponse created = productService.create(request);

        assertThat(created.name()).isEqualTo("Lamp");
        assertThat(created.price()).isEqualTo(2_500L);
        assertThat(created.createdAt()).isNotNull();
    }

    @Test
    void listAppendsTheIdAsATieBreakerToTheRequestedSort() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        Pageable requested = PageRequest.of(2, 10, Sort.by("category"));

        productService.list(NO_FILTER, requested);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), captor.capture());
        Pageable used = captor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(10);
        assertThat(used.getSort().stream().map(Sort.Order::getProperty)).containsExactly("category", "id");
    }

    @Test
    void listKeepsASortThatAlreadyOrdersById() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        Pageable requested = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        productService.list(NO_FILTER, requested);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(requested.getSort());
    }

    @Test
    void listMapsTheMatchesAndKeepsTheTotals() {
        Product product = product();
        Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 1), 41);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ProductResponse> result = productService.list(NO_FILTER, PageRequest.of(0, 1));

        assertThat(result.getContent()).extracting(ProductResponse::name).containsExactly("Lamp");
        assertThat(result.getTotalElements()).isEqualTo(41);
        assertThat(result.getTotalPages()).isEqualTo(41);
    }

    @Test
    void listAcceptsThePageSizeCap() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        productService.list(NO_FILTER, PageRequest.of(0, ProductService.MAX_PAGE_SIZE));

        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRejectsAPageSizeAboveTheCap() {
        Pageable tooBig = PageRequest.of(0, ProductService.MAX_PAGE_SIZE + 1);

        InvalidRequestParameterException thrown =
                assertThrows(InvalidRequestParameterException.class, () -> productService.list(NO_FILTER, tooBig));

        assertThat(thrown.getParameter()).isEqualTo("size");
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRejectsAPageThatOverflowsTheOffset() {
        Pageable overflowing = PageRequest.of(Integer.MAX_VALUE, 100);

        InvalidRequestParameterException thrown =
                assertThrows(InvalidRequestParameterException.class, () -> productService.list(NO_FILTER, overflowing));

        assertThat(thrown.getParameter()).isEqualTo("page");
    }

    @Test
    void listRejectsANegativeMinPrice() {
        ProductFilter filter = new ProductFilter(null, -1L, null, null, null);

        InvalidRequestParameterException thrown = assertThrows(InvalidRequestParameterException.class,
                () -> productService.list(filter, PageRequest.of(0, 10)));

        assertThat(thrown.getParameter()).isEqualTo("minPrice");
    }

    @Test
    void listRejectsANegativeMaxPrice() {
        ProductFilter filter = new ProductFilter(null, null, -1L, null, null);

        InvalidRequestParameterException thrown = assertThrows(InvalidRequestParameterException.class,
                () -> productService.list(filter, PageRequest.of(0, 10)));

        assertThat(thrown.getParameter()).isEqualTo("maxPrice");
    }

    @Test
    void listRejectsAMinPriceAboveTheMaxPrice() {
        ProductFilter filter = new ProductFilter(null, 5_000L, 1_000L, null, null);

        InvalidRequestParameterException thrown = assertThrows(InvalidRequestParameterException.class,
                () -> productService.list(filter, PageRequest.of(0, 10)));

        assertThat(thrown.getParameter()).isEqualTo("minPrice");
        assertThat(thrown.getReason()).isEqualTo("cannot be greater than maxPrice");
    }

    @Test
    void getReturnsTheProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));

        ProductResponse found = productService.get(PRODUCT_ID);

        assertThat(found.name()).isEqualTo("Lamp");
    }

    @Test
    void getThrowsWhenTheProductDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.get(PRODUCT_ID));
    }

    @Test
    void updateReplacesEveryEditableField() {
        Product existing = product();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);
        ProductRequest request = new ProductRequest("Desk Lamp", "Office", 3_100L, 9, 4.9);

        ProductResponse updated = productService.update(PRODUCT_ID, request);

        assertThat(updated.name()).isEqualTo("Desk Lamp");
        assertThat(updated.category()).isEqualTo("Office");
        assertThat(updated.price()).isEqualTo(3_100L);
        assertThat(updated.stock()).isEqualTo(9);
        assertThat(updated.rating()).isEqualTo(4.9);
    }

    @Test
    void updateThrowsAndSavesNothingWhenTheProductDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());
        ProductRequest request = new ProductRequest("Desk Lamp", "Office", 3_100L, 9, 4.9);

        assertThrows(ProductNotFoundException.class, () -> productService.update(PRODUCT_ID, request));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteRemovesTheProduct() {
        Product existing = product();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existing));

        productService.delete(PRODUCT_ID);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteThrowsWhenTheProductDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.delete(PRODUCT_ID));

        verify(productRepository, never()).delete(any(Product.class));
    }

    private Product product() {
        return Product.builder()
                .id(PRODUCT_ID)
                .name("Lamp")
                .category("Home")
                .price(2_500)
                .stock(5)
                .rating(4.5)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
