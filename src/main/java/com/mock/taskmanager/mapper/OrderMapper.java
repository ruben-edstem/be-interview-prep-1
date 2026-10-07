package com.mock.taskmanager.mapper;

import com.mock.taskmanager.dto.response.OrderItemResponse;
import com.mock.taskmanager.dto.response.OrderResponse;
import com.mock.taskmanager.entity.CustomerOrder;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getProductId(), item.getQuantity()))
                .sorted(Comparator.comparing(OrderItemResponse::productId))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), items, order.getCreatedAt());
    }
}
