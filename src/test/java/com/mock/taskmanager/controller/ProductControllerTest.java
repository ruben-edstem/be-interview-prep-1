package com.mock.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.exception.ProductNotFoundException;
import com.mock.taskmanager.service.ProductService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createReturns201WithTheCreatedProduct() throws Exception {
        when(productService.create(any(ProductRequest.class)))
                .thenReturn(new ProductResponse(PRODUCT_ID, "Widget", 10));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Widget\", \"stock\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.stock").value(10));
    }

    @Test
    void createWithNegativeStockReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Widget\", \"stock\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("stock"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("stock cannot be negative"));

        verifyNoInteractions(productService);
    }

    @Test
    void createWithABlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" \", \"stock\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void createWithoutStockReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Widget\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("stock is required"));
    }

    @Test
    void getReturnsTheProduct() throws Exception {
        when(productService.get(PRODUCT_ID)).thenReturn(new ProductResponse(PRODUCT_ID, "Widget", 3));

        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Widget"))
                .andExpect(jsonPath("$.data.stock").value(3));
    }

    @Test
    void getUnknownProductReturns404() throws Exception {
        when(productService.get(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
