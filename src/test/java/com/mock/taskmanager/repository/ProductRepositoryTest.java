package com.mock.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mock.taskmanager.dto.request.ProductFilter;
import com.mock.taskmanager.entity.Product;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.save(product("Red Lamp", "Home", 2_500, 5, 4.5));
        productRepository.save(product("Blue Lamp", "Home", 4_000, 0, 3.5));
        productRepository.save(product("Trail Backpack", "Sports", 9_000, 12, 4.8));
        productRepository.save(product("Novel", "Books", 1_200, 30, 4.0));
        productRepository.save(product("100% Cotton Shirt", "Clothing", 3_000, 8, 2.9));
    }

    @Test
    void anEmptyFilterReturnsEveryProduct() {
        Page<Product> page = find(new ProductFilter(null, null, null, null, null));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void categoryFilterReturnsOnlyThatCategory() {
        Page<Product> page = find(new ProductFilter("Home", null, null, null, null));

        assertThat(page.getContent()).extracting(Product::getName).containsExactlyInAnyOrder("Red Lamp", "Blue Lamp");
    }

    @Test
    void priceRangeIsInclusiveOnBothEnds() {
        Page<Product> page = find(new ProductFilter(null, 2_500L, 4_000L, null, null));

        assertThat(page.getContent()).extracting(Product::getName)
                .containsExactlyInAnyOrder("Red Lamp", "Blue Lamp", "100% Cotton Shirt");
    }

    @Test
    void inStockOnlyExcludesProductsWithNoStock() {
        Page<Product> page = find(new ProductFilter("Home", null, null, true, null));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Red Lamp");
    }

    @Test
    void inStockFalseDoesNotFilterOutAnything() {
        Page<Product> page = find(new ProductFilter("Home", null, null, false, null));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void nameSearchIsCaseInsensitiveAndMatchesAnywhereInTheName() {
        Page<Product> page = find(new ProductFilter(null, null, null, null, "LAMP"));

        assertThat(page.getContent()).extracting(Product::getName).containsExactlyInAnyOrder("Red Lamp", "Blue Lamp");
    }

    @Test
    void nameSearchTreatsLikeWildcardsAsPlainText() {
        Page<Product> percent = find(new ProductFilter(null, null, null, null, "%"));
        Page<Product> underscore = find(new ProductFilter(null, null, null, null, "_"));

        assertThat(percent.getContent()).extracting(Product::getName).containsExactly("100% Cotton Shirt");
        assertThat(underscore.getContent()).isEmpty();
    }

    @Test
    void anEmptyCategoryIsIgnored() {
        Page<Product> page = find(new ProductFilter("", null, null, null, null));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void aBlankNameIsIgnored() {
        Page<Product> page = find(new ProductFilter(null, null, null, null, "   "));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void spacesAroundTheNameSearchAreTrimmed() {
        Page<Product> page = find(new ProductFilter(null, null, null, null, "  lamp "));

        assertThat(page.getContent()).extracting(Product::getName).containsExactlyInAnyOrder("Red Lamp", "Blue Lamp");
    }

    @Test
    void everyFilterCombinesInOneQuery() {
        ProductFilter filter = new ProductFilter("Home", 1_000L, 3_000L, true, "lamp");

        Page<Product> page = find(filter);

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Red Lamp");
    }

    @Test
    void filtersThatExcludeEverythingReturnAnEmptyPage() {
        Page<Product> page = find(new ProductFilter("Books", 5_000L, null, null, null));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void sortingByPriceDescendingOrdersTheMatches() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "price"));

        Page<Product> page = productRepository.findAll(
                ProductSpecifications.matching(new ProductFilter(null, null, null, null, null)), pageable);

        assertThat(page.getContent()).extracting(Product::getPrice)
                .containsExactly(9_000L, 4_000L, 3_000L, 2_500L, 1_200L);
    }

    @Test
    void totalCountAndPagesDescribeTheWholeMatchNotTheCurrentPage() {
        PageRequest pageable = PageRequest.of(1, 2, Sort.by("name"));

        Page<Product> page = productRepository.findAll(
                ProductSpecifications.matching(new ProductFilter(null, null, null, null, null)), pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    private Page<Product> find(ProductFilter filter) {
        return productRepository.findAll(ProductSpecifications.matching(filter), PageRequest.of(0, 10));
    }

    private Product product(String name, String category, long price, int stock, double rating) {
        return Product.builder()
                .name(name)
                .category(category)
                .price(price)
                .stock(stock)
                .rating(rating)
                .createdAt(Instant.now())
                .build();
    }
}
