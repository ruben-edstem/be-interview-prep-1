package com.mock.taskmanager.dto.response;

import java.util.UUID;

public record OrderItemResponse(UUID productId, long quantity) {
}
