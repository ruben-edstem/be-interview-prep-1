package com.mock.taskmanager.controller;

import com.mock.taskmanager.dto.request.ProductRequest;
import com.mock.taskmanager.dto.response.ApiResponse;
import com.mock.taskmanager.dto.response.ProductResponse;
import com.mock.taskmanager.service.ProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(id));
    }
}
