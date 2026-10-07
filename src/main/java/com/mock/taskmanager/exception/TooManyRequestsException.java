package com.mock.taskmanager.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Too many failed login attempts, try again later");
    }
}
