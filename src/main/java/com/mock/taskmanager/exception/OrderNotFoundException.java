package com.mock.taskmanager.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found: " + id);
    }
}
