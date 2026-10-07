package com.mock.taskmanager.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mock.taskmanager.config.RateLimitProperties;
import com.mock.taskmanager.exception.TooManyRequestsException;
import com.mock.taskmanager.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new LoginAttemptLimiter(new RateLimitProperties(30, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)), clock);
    }

    @Test
    void allowsAttemptsWhileFailuresAreBelowTheLimit() {
        limiter.recordFailure("ada@example.com");
        limiter.recordFailure("ada@example.com");

        assertDoesNotThrow(() -> limiter.assertAllowed("ada@example.com"));
    }

    @Test
    void blocksAnEmailOnceItHasTooManyFailures() {
        limiter.recordFailure("ada@example.com");
        limiter.recordFailure("ada@example.com");
        limiter.recordFailure("ada@example.com");

        assertThrows(TooManyRequestsException.class, () -> limiter.assertAllowed("ada@example.com"));
        assertDoesNotThrow(() -> limiter.assertAllowed("grace@example.com"));
    }

    @Test
    void aSuccessfulLoginClearsTheFailures() {
        limiter.recordFailure("ada@example.com");
        limiter.recordFailure("ada@example.com");
        limiter.reset("ada@example.com");
        limiter.recordFailure("ada@example.com");

        assertDoesNotThrow(() -> limiter.assertAllowed("ada@example.com"));
    }

    @Test
    void unblocksAfterTheFailureWindow() {
        limiter.recordFailure("ada@example.com");
        limiter.recordFailure("ada@example.com");
        limiter.recordFailure("ada@example.com");

        clock.advance(Duration.ofMinutes(15));

        assertDoesNotThrow(() -> limiter.assertAllowed("ada@example.com"));
    }
}
