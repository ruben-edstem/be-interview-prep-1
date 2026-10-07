package com.mock.taskmanager.dto.request;

import com.mock.taskmanager.entity.ShortUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

public record ShortenRequest(
        @NotBlank
        @Size(max = ShortUrl.MAX_URL_LENGTH)
        @URL(regexp = "^https?://.+")
        String url,

        @Future
        Instant expiresAt) {
}
