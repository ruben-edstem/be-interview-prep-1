package com.mock.taskmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.dto.request.ProductFilter;
import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.service.ProductService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String VALID_BODY = """
            {"name": "Lamp", "category": "Home", "price": 2500, "stock": 5, "rating": 4.5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void listReturnsThePageWithTheTotalCountAndNumberOfPages() throws Exception {
        PageImpl<ProductResponse> page = new PageImpl<>(List.of(response()), PageRequest.of(1, 1), 41);
        when(productService.list(any(ProductFilter.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Lamp"))
                .andExpect(jsonPath("$.data.page.totalElements").value(41))
                .andExpect(jsonPath("$.data.page.totalPages").value(41))
                .andExpect(jsonPath("$.data.page.number").value(1));
    }

    @Test
    void listPassesEveryFilterAndTheSortToTheService() throws Exception {
        when(productService.list(any(ProductFilter.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "Home")
                        .param("minPrice", "1000")
                        .param("maxPrice", "5000")
                        .param("inStock", "true")
                        .param("name", "lamp")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).list(filter.capture(), pageable.capture());
        assertThat(filter.getValue()).isEqualTo(new ProductFilter("Home", 1_000L, 5_000L, true, "lamp"));
        assertThat(pageable.getValue().getSort().getOrderFor("price").isDescending()).isTrue();
    }

    @Test
    void listWithoutFiltersPassesAnEmptyFilter() throws Exception {
        when(productService.list(any(ProductFilter.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        verify(productService).list(eq(new ProductFilter(null, null, null, null, null)), any(Pageable.class));
    }

    @Test
    void listWithANonNumericPriceReturns400NamingTheParameter() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("minPrice", "cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"));
    }

    @Test
    void listWithAPageSizeAboveTheCapReturns400() throws Exception {
        when(productService.list(any(ProductFilter.class), any(Pageable.class)))
                .thenThrow(new InvalidRequestParameterException("size", "must be at most 100"));

        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be at most 100"));
    }

    @Test
    void getReturnsTheProduct() throws Exception {
        when(productService.get(PRODUCT_ID)).thenReturn(response());

        mockMvc.perform(get("/api/v1/products/" + PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.price").value(2_500));
    }

    @Test
    void getAnUnknownProductReturns404() throws Exception {
        when(productService.get(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(get("/api/v1/products/" + PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void getWithAMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void createReturns201WithTheCreatedProduct() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Lamp"));
    }

    @Test
    void createWithAnInvalidBodyReturns400WithAMessageForEachField() throws Exception {
        String body = """
                {"name": " ", "category": "Home", "price": -1, "stock": -2, "rating": 5.5}
                """;

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
    }

    @Test
    void createWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
    }

    @Test
    void updateReturnsTheUpdatedProduct() throws Exception {
        when(productService.update(eq(PRODUCT_ID), any(ProductRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/v1/products/" + PRODUCT_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID.toString()));
    }

    @Test
    void updateAnUnknownProductReturns404() throws Exception {
        when(productService.update(eq(PRODUCT_ID), any(ProductRequest.class)))
                .thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(put("/api/v1/products/" + PRODUCT_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/" + PRODUCT_ID)).andExpect(status().isNoContent());

        verify(productService).delete(PRODUCT_ID);
    }

    @Test
    void deleteAnUnknownProductReturns404() throws Exception {
        doThrow(new ProductNotFoundException(PRODUCT_ID)).when(productService).delete(PRODUCT_ID);

        mockMvc.perform(delete("/api/v1/products/" + PRODUCT_ID)).andExpect(status().isNotFound());
    }

    private ProductResponse response() {
        return new ProductResponse(PRODUCT_ID, "Lamp", "Home", 2_500, 5, 4.5, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
