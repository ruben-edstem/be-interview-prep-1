package com.mock.taskmanager.controller;

import com.mock.taskmanager.dto.request.OrderRequest;
import com.mock.taskmanager.dto.response.ApiResponse;
import com.mock.taskmanager.dto.response.OrderResponse;
import com.mock.taskmanager.service.OrderPlacement;
import com.mock.taskmanager.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {
        OrderPlacement placement = orderService.place(idempotencyKey, request);
        HttpStatus status = placement.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(placement.order()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.cancel(id));
    }
}
