package com.mock.taskmanager.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("30") @Min(1) int ipRequests,
        @DefaultValue("1m") Duration ipWindow,
        @DefaultValue("5") @Min(1) int loginFailures,
        @DefaultValue("15m") Duration loginFailureWindow) {
}
