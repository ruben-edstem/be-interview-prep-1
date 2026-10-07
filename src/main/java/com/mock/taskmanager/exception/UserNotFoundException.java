package com.mock.taskmanager.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found: " + id);
    }
}
