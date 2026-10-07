package com.edstem.interviewprep.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edstem.interviewprep.urlshortener.config.ShortenerProperties;
import com.edstem.interviewprep.urlshortener.dto.request.ShortenRequest;
import com.edstem.interviewprep.urlshortener.dto.response.ShortenResponse;
import com.edstem.interviewprep.urlshortener.dto.response.StatsResponse;
import com.edstem.interviewprep.urlshortener.entity.ShortUrl;
import com.edstem.interviewprep.urlshortener.exception.CodeGenerationException;
import com.edstem.interviewprep.urlshortener.exception.ShortUrlExpiredException;
import com.edstem.interviewprep.urlshortener.exception.ShortUrlNotFoundException;
import com.edstem.interviewprep.urlshortener.repository.ShortUrlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private CodeGenerator codeGenerator;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlService(repository, codeGenerator,
                new ShortenerProperties("http://short.test/"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shortenStoresUrlAndReturnsShortUrl() {
        Instant expiry = NOW.plusSeconds(3600);
        when(codeGenerator.generate()).thenReturn("abc12345");
        when(repository.existsByCode("abc12345")).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

        ShortenResponse response = service.shorten(
                new ShortenRequest("https://example.com/a", expiry));

        ArgumentCaptor<ShortUrl> saved = ArgumentCaptor.forClass(ShortUrl.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOriginalUrl()).isEqualTo("https://example.com/a");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(expiry);
        assertThat(response.code()).isEqualTo("abc12345");
        assertThat(response.shortUrl()).isEqualTo("http://short.test/abc12345");
        assertThat(response.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void shortenIssuesDistinctCodesForTheSameUrl() {
        when(codeGenerator.generate()).thenReturn("aaaaaaaa", "bbbbbbbb");
        when(repository.existsByCode(any())).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));
        ShortenRequest request = new ShortenRequest("https://example.com/a", null);

        ShortenResponse first = service.shorten(request);
        ShortenResponse second = service.shorten(request);

        assertThat(first.code()).isNotEqualTo(second.code());
    }

    @Test
    void shortenSkipsCodeThatAlreadyExists() {
        when(codeGenerator.generate()).thenReturn("taken123", "free4567");
        when(repository.existsByCode("taken123")).thenReturn(true);
        when(repository.existsByCode("free4567")).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class))).thenAnswer(i -> i.getArgument(0));

        ShortenResponse response = service.shorten(new ShortenRequest("https://example.com", null));

        assertThat(response.code()).isEqualTo("free4567");
    }

    @Test
    void shortenRetriesWhenInsertLosesCollisionRace() {
        when(codeGenerator.generate()).thenReturn("race1234", "free4567");
        when(repository.existsByCode(any())).thenReturn(false);
        when(repository.saveAndFlush(any(ShortUrl.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(i -> i.getArgument(0));

        ShortenResponse response = service.shorten(new ShortenRequest("https://example.com", null));

        assertThat(response.code()).isEqualTo("free4567");
    }

    @Test
    void shortenFailsAfterMaxAttempts() {
        when(codeGenerator.generate()).thenReturn("taken123");
        when(repository.existsByCode("taken123")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(new ShortenRequest("https://example.com", null)))
                .isInstanceOf(CodeGenerationException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void resolveReturnsOriginalUrlAndCountsVisit() {
        when(repository.findByCode("abc12345")).thenReturn(Optional.of(shortUrl(null)));

        String url = service.resolve("abc12345");

        assertThat(url).isEqualTo("https://example.com/a");
        verify(repository).incrementVisitCount("abc12345");
    }

    @Test
    void resolveAllowsLinkThatExpiresInTheFuture() {
        when(repository.findByCode("abc12345"))
                .thenReturn(Optional.of(shortUrl(NOW.plusSeconds(1))));

        String url = service.resolve("abc12345");

        assertThat(url).isEqualTo("https://example.com/a");
    }

    @Test
    void resolveRejectsUnknownCodeWithoutCountingVisit() {
        when(repository.findByCode("missing1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing1"))
                .isInstanceOf(ShortUrlNotFoundException.class);
        verify(repository, never()).incrementVisitCount(any());
    }

    @Test
    void resolveRejectsExpiredLinkWithoutCountingVisit() {
        when(repository.findByCode("abc12345")).thenReturn(Optional.of(shortUrl(NOW)));

        assertThatThrownBy(() -> service.resolve("abc12345"))
                .isInstanceOf(ShortUrlExpiredException.class);
        verify(repository, never()).incrementVisitCount(any());
    }

    @Test
    void statsReturnsUrlVisitCountAndCreatedDate() {
        when(repository.findByCode("abc12345")).thenReturn(Optional.of(shortUrl(null)));

        StatsResponse stats = service.stats("abc12345");

        assertThat(stats.code()).isEqualTo("abc12345");
        assertThat(stats.originalUrl()).isEqualTo("https://example.com/a");
        assertThat(stats.visitCount()).isZero();
    }

    @Test
    void statsRejectsUnknownCode() {
        when(repository.findByCode("missing1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stats("missing1"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    private ShortUrl shortUrl(Instant expiresAt) {
        return ShortUrl.builder()
                .code("abc12345")
                .originalUrl("https://example.com/a")
                .expiresAt(expiresAt)
                .build();
    }
}
