package com.mock.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.dto.request.OrderRequest;
import com.mock.taskmanager.dto.response.OrderItemResponse;
import com.mock.taskmanager.dto.response.OrderResponse;
import com.mock.taskmanager.entity.OrderStatus;
import com.mock.taskmanager.exception.IdempotencyKeyReuseException;
import com.mock.taskmanager.exception.InsufficientStockException;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.OrderNotFoundException;
import com.mock.taskmanager.service.OrderPlacement;
import com.mock.taskmanager.service.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BODY = """
            {"items": [{"productId": "22222222-2222-2222-2222-222222222222", "quantity": 2}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void placeReturns201WithTheCreatedOrder() throws Exception {
        when(orderService.place(eq("key-1"), any(OrderRequest.class)))
                .thenReturn(new OrderPlacement(response(OrderStatus.PLACED), false));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PLACED"))
                .andExpect(jsonPath("$.data.items[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test
    void placeReturns200WithTheOriginalOrderForARetry() throws Exception {
        when(orderService.place(eq("key-1"), any(OrderRequest.class)))
                .thenReturn(new OrderPlacement(response(OrderStatus.PLACED), true));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()));
    }

    @Test
    void placeReturns409WithAClearMessageWhenStockIsShort() throws Exception {
        when(orderService.place(eq("key-1"), any(OrderRequest.class)))
                .thenThrow(new InsufficientStockException(PRODUCT_ID, 2, 1));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message")
                        .value("Insufficient stock for product " + PRODUCT_ID + ": requested 2, available 1"));
    }

    @Test
    void placeReturns422WhenTheKeyWasUsedForADifferentRequest() throws Exception {
        when(orderService.place(eq("key-1"), any(OrderRequest.class)))
                .thenThrow(new IdempotencyKeyReuseException());

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void placeWithoutAnIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(orderService);
    }

    @Test
    void placeWithAnInvalidIdempotencyKeyReturns400NamingTheHeader() throws Exception {
        when(orderService.place(eq(" "), any(OrderRequest.class)))
                .thenThrow(new InvalidRequestParameterException("Idempotency-Key", "must not be blank"));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
    }

    @Test
    void placeWithNoItemsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"));

        verifyNoInteractions(orderService);
    }

    @Test
    void placeWithAZeroQuantityReturns400() throws Exception {
        String body = """
                {"items": [{"productId": "%s", "quantity": 0}]}
                """.formatted(PRODUCT_ID);

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("quantity must be at least 1"));
    }

    @Test
    void placeWithAMissingProductIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\": [{\"quantity\": 1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("productId is required"));
    }

    @Test
    void placeWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getReturnsTheOrder() throws Exception {
        when(orderService.get(ORDER_ID)).thenReturn(response(OrderStatus.CANCELLED));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        when(orderService.get(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelReturnsTheCancelledOrder() throws Exception {
        when(orderService.cancel(ORDER_ID)).thenReturn(response(OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(orderService).cancel(ORDER_ID);
    }

    @Test
    void cancelUnknownOrderReturns404() throws Exception {
        when(orderService.cancel(ORDER_ID)).thenThrow(new OrderNotFoundException(ORDER_ID));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders/not-a-uuid/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    private OrderResponse response(OrderStatus status) {
        return new OrderResponse(ORDER_ID, status, List.of(new OrderItemResponse(PRODUCT_ID, 2)), Instant.now());
    }
}
