package com.mock.taskmanager.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderRequest(
        @NotEmpty(message = "items must contain at least one item")
        @Size(max = 100, message = "items must contain at most 100 items")
        List<@NotNull(message = "items must not contain null") @Valid OrderItemRequest> items) {
}
