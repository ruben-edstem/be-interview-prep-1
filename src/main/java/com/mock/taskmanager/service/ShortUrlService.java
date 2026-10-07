package com.mock.taskmanager.service;

import com.mock.taskmanager.config.ShortenerProperties;
import com.mock.taskmanager.dto.request.ShortenRequest;
import com.mock.taskmanager.dto.response.ShortenResponse;
import com.mock.taskmanager.dto.response.StatsResponse;
import com.mock.taskmanager.entity.ShortUrl;
import com.mock.taskmanager.exception.CodeGenerationException;
import com.mock.taskmanager.exception.ShortUrlExpiredException;
import com.mock.taskmanager.exception.ShortUrlNotFoundException;
import com.mock.taskmanager.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlService {

    static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final CodeGenerator codeGenerator;
    private final ShortenerProperties properties;
    private final Clock clock;

    public ShortenResponse shorten(ShortenRequest request) {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (repository.existsByCode(code)) {
                continue;
            }
            try {
                ShortUrl saved = repository.saveAndFlush(ShortUrl.builder()
                        .code(code)
                        .originalUrl(request.url())
                        .expiresAt(request.expiresAt())
                        .build());
                return toShortenResponse(saved);
            } catch (DataIntegrityViolationException e) {
                log.warn("Short code collision on attempt {}", attempt);
            }
        }
        throw new CodeGenerationException(MAX_CODE_ATTEMPTS);
    }

    @Transactional
    public String resolve(String code) {
        ShortUrl shortUrl = repository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        if (shortUrl.isExpiredAt(Instant.now(clock))) {
            throw new ShortUrlExpiredException(code);
        }
        repository.incrementVisitCount(code);
        return shortUrl.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String code) {
        ShortUrl shortUrl = repository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        return new StatsResponse(shortUrl.getCode(), shortUrl.getOriginalUrl(),
                shortUrl.getVisitCount(), shortUrl.getCreatedAt(), shortUrl.getExpiresAt());
    }

    private ShortenResponse toShortenResponse(ShortUrl shortUrl) {
        String base = properties.baseUrl().replaceAll("/+$", "");
        return new ShortenResponse(shortUrl.getCode(), base + "/" + shortUrl.getCode(),
                shortUrl.getOriginalUrl(), shortUrl.getExpiresAt());
    }
}
