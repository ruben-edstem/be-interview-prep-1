package com.mock.taskmanager.dto.response;

import java.time.Instant;

public record StatsResponse(String code, String originalUrl, long visitCount, Instant createdAt,
                            Instant expiresAt) {
}
