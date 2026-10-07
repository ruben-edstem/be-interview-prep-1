package com.mock.taskmanager.service;

import com.mock.taskmanager.dto.response.OrderResponse;

public record OrderPlacement(OrderResponse order, boolean replayed) {
}
