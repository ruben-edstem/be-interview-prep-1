package com.edstem.taskmanager.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class TaskNotFoundException extends ApiException {

    public TaskNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found: " + id);
    }
}
