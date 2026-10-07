package com.mock.taskmanager.controller;

import com.mock.taskmanager.service.ShortUrlService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final ShortUrlService service;

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(
            @PathVariable @Pattern(regexp = CodePattern.REGEX) String code) {
        String originalUrl = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
