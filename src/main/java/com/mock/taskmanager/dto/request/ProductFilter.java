package com.mock.taskmanager.dto.request;

public record ProductFilter(
        String category,
        Long minPrice,
        Long maxPrice,
        Boolean inStock,
        String name) {
}
