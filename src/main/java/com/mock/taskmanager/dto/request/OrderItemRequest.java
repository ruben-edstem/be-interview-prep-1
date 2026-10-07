package com.mock.taskmanager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "productId is required")
        UUID productId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 1_000_000, message = "quantity must be at most 1000000")
        Long quantity) {
}
