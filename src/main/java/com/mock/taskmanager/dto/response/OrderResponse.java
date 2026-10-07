package com.mock.taskmanager.dto.response;

import com.mock.taskmanager.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, OrderStatus status, List<OrderItemResponse> items, Instant createdAt) {
}
