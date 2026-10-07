package com.edstem.interviewprep.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.urlshortener.dto.request.ShortenRequest;
import com.edstem.interviewprep.urlshortener.dto.response.ShortenResponse;
import com.edstem.interviewprep.urlshortener.dto.response.StatsResponse;
import com.edstem.interviewprep.urlshortener.exception.ShortUrlNotFoundException;
import com.edstem.interviewprep.urlshortener.service.ShortUrlService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService service;

    @Test
    void shortenReturnsCreatedWithShortUrl() throws Exception {
        when(service.shorten(any(ShortenRequest.class))).thenReturn(
                new ShortenResponse("abc12345", "http://localhost:8080/abc12345",
                        "https://example.com/a", null));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("abc12345"))
                .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8080/abc12345"));
    }

    @Test
    void shortenPassesExpiryToService() throws Exception {
        Instant expiry = Instant.parse("2099-01-01T00:00:00Z");
        when(service.shorten(any(ShortenRequest.class))).thenReturn(
                new ShortenResponse("abc12345", "http://localhost:8080/abc12345",
                        "https://example.com/a", expiry));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expiresAt").value("2099-01-01T00:00:00Z"));

        verify(service).shorten(new ShortenRequest("https://example.com/a", expiry));
    }

    @Test
    void shortenRejectsInvalidUrls() throws Exception {
        String[] invalidBodies = {
                "{\"url\":\"not a url\"}",
                "{\"url\":\"ftp://example.com/file\"}",
                "{\"url\":\"javascript:alert(1)\"}",
                "{\"url\":\"\"}",
                "{}",
        };

        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        verify(service, never()).shorten(any());
    }

    @Test
    void shortenRejectsExpiryInThePast() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).shorten(any());
    }

    @Test
    void shortenRejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\",\"expiresAt\":\"tomorrow\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void statsReturnsVisitCountAndCreatedDate() throws Exception {
        when(service.stats("abc12345")).thenReturn(new StatsResponse("abc12345",
                "https://example.com/a", 7, Instant.parse("2026-10-07T10:00:00Z"), null));

        mockMvc.perform(get("/api/v1/urls/abc12345/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl").value("https://example.com/a"))
                .andExpect(jsonPath("$.data.visitCount").value(7))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-07T10:00:00Z"));
    }

    @Test
    void statsReturnsNotFoundForUnknownCode() throws Exception {
        when(service.stats("missing1")).thenThrow(new ShortUrlNotFoundException("missing1"));

        mockMvc.perform(get("/api/v1/urls/missing1/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void statsRejectsMalformedCode() throws Exception {
        mockMvc.perform(get("/api/v1/urls/waytoolongcode/stats"))
                .andExpect(status().isBadRequest());

        verify(service, never()).stats(any());
    }
}
