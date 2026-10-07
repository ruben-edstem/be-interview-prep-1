package com.edstem.interviewprep.urlshortener.controller;

import com.edstem.interviewprep.common.dto.ApiResponse;
import com.edstem.interviewprep.urlshortener.dto.request.ShortenRequest;
import com.edstem.interviewprep.urlshortener.dto.response.ShortenResponse;
import com.edstem.interviewprep.urlshortener.dto.response.StatsResponse;
import com.edstem.interviewprep.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService service;

    @PostMapping
    public ResponseEntity<ApiResponse<ShortenResponse>> shorten(
            @Valid @RequestBody ShortenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.shorten(request)));
    }

    @GetMapping("/{code}/stats")
    public ApiResponse<StatsResponse> stats(
            @PathVariable @Pattern(regexp = CodePattern.REGEX) String code) {
        return ApiResponse.ok(service.stats(code));
    }
}
