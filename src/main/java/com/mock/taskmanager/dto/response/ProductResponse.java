package com.mock.taskmanager.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String category,
        long price,
        int stock,
        double rating,
        Instant createdAt) {
}
