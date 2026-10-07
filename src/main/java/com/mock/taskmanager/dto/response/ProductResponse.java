package com.mock.taskmanager.dto.response;

import java.util.UUID;

public record ProductResponse(UUID id, String name, long stock) {
}
