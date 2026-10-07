package com.mock.taskmanager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotNull(message = "stock is required")
        @Min(value = 0, message = "stock cannot be negative")
        @Max(value = 1_000_000_000, message = "stock must be at most 1000000000")
        Long stock) {
}
