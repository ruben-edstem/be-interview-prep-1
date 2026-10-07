package com.mock.taskmanager.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(UUID productId, long requested, long available) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Insufficient stock for product %s: requested %d, available %d"
                        .formatted(productId, requested, available));
    }
}
