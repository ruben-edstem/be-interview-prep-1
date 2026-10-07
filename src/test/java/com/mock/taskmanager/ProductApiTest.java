package com.mock.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.repository.ProductRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void theCatalogIsSeededWith100Products() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(100))
                .andExpect(jsonPath("$.data.page.totalPages").value(10))
                .andExpect(jsonPath("$.data.content.length()").value(10));
    }

    @Test
    void allFiltersCombinedInOneRequestMatchTheStoredProducts() throws Exception {
        List<Product> expected = productRepository.findAll().stream()
                .filter(product -> product.getCategory().equals("Electronics"))
                .filter(product -> product.getPrice() >= 5_000 && product.getPrice() <= 30_000)
                .filter(product -> product.getStock() > 0)
                .filter(product -> product.getName().toLowerCase().contains("a"))
                .toList();
        assertThat(expected).isNotEmpty();

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "Electronics")
                        .param("minPrice", "5000")
                        .param("maxPrice", "30000")
                        .param("inStock", "true")
                        .param("name", "A")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(expected.size()))
                .andExpect(jsonPath("$.data.content.length()").value(expected.size()));
    }

    @Test
    void filtersAndSortingAndPagingWorkTogether() throws Exception {
        String body = mockMvc.perform(get("/api/v1/products")
                        .param("category", "Books")
                        .param("inStock", "true")
                        .param("sort", "price,desc")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(body).at("/data/content");
        long previous = Long.MAX_VALUE;
        for (JsonNode product : content) {
            assertThat(product.get("category").asString()).isEqualTo("Books");
            assertThat(product.get("stock").asInt()).isPositive();
            assertThat(product.get("price").asLong()).isLessThanOrEqualTo(previous);
            previous = product.get("price").asLong();
        }
        assertThat(content.size()).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "category", "price", "stock", "rating", "createdAt", "id"})
    void everyFieldCanBeUsedAsTheSort(String field) throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", field + ",desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(20));
    }

    @Test
    void sortingByAColumnWithManyTiesStillPagesWithoutRepeatsOrGaps() throws Exception {
        Set<String> seen = new HashSet<>();

        for (int page = 0; page < 10; page++) {
            String body = mockMvc.perform(get("/api/v1/products")
                            .param("sort", "category")
                            .param("page", String.valueOf(page))
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            for (JsonNode product : objectMapper.readTree(body).at("/data/content")) {
                seen.add(product.get("id").asString());
            }
        }

        assertThat(seen).hasSize(100);
    }

    @Test
    void thePageSizeCapIsAcceptedAtExactly100() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(100));
    }

    @Test
    void aPageSizeAbove100Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    @Test
    void anUnknownSortFieldReturns400NamingTheSortParameter() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("category", "Books").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("cannot sort by nope"));
    }

    @Test
    void aPriceRangeWithMinAboveMaxReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("minPrice", "9000").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"));
    }

    @Test
    void aSearchThatMatchesNothingReturnsAnEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("name", "zzz-no-such-product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.page.totalElements").value(0))
                .andExpect(jsonPath("$.data.page.totalPages").value(0));
    }
}
