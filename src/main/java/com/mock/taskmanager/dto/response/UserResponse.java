package com.mock.taskmanager.dto.response;

import com.mock.taskmanager.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, Instant createdAt) {
}
