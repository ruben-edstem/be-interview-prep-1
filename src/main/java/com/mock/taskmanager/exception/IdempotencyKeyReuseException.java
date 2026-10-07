package com.mock.taskmanager.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReuseException extends ApiException {

    public IdempotencyKeyReuseException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key was already used with a different request");
    }
}
