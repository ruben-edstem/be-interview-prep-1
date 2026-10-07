package com.mock.taskmanager.dto.response;

import java.time.Instant;

public record ShortenResponse(String code, String shortUrl, String originalUrl, Instant expiresAt) {
}
