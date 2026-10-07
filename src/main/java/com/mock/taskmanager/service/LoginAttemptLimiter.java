package com.mock.taskmanager.service;

import com.mock.taskmanager.config.RateLimitProperties;
import com.mock.taskmanager.exception.TooManyRequestsException;
import com.mock.taskmanager.security.RateLimiter;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptLimiter {

    private final RateLimiter failures;

    public LoginAttemptLimiter(RateLimitProperties properties, Clock clock) {
        this.failures = new RateLimiter(properties.loginFailures(), properties.loginFailureWindow(), clock);
    }

    public void assertAllowed(String email) {
        if (failures.isBlocked(email)) {
            throw new TooManyRequestsException();
        }
    }

    public void recordFailure(String email) {
        failures.record(email);
    }

    public void reset(String email) {
        failures.reset(email);
    }
}
