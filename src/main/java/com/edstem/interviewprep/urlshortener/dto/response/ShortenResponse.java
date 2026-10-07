package com.edstem.interviewprep.urlshortener.dto.response;

import java.time.Instant;

public record ShortenResponse(String code, String shortUrl, String originalUrl, Instant expiresAt) {
}
