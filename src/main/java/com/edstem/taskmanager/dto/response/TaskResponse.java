package com.edstem.taskmanager.dto.response;

import com.edstem.taskmanager.entity.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        Instant createdAt) {
}
