package com.mock.taskmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl) {
}
