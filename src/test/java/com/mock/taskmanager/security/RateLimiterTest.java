package com.mock.taskmanager.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mock.taskmanager.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private MutableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new RateLimiter(3, Duration.ofMinutes(1), clock);
    }

    @Test
    void tryAcquireAllowsUpToTheLimitThenRefuses() {
        boolean first = limiter.tryAcquire("client");
        boolean second = limiter.tryAcquire("client");
        boolean third = limiter.tryAcquire("client");
        boolean fourth = limiter.tryAcquire("client");

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isTrue();
        assertThat(fourth).isFalse();
    }

    @Test
    void tryAcquireCountsEachKeySeparately() {
        limiter.tryAcquire("client-a");
        limiter.tryAcquire("client-a");
        limiter.tryAcquire("client-a");

        assertThat(limiter.tryAcquire("client-a")).isFalse();
        assertThat(limiter.tryAcquire("client-b")).isTrue();
    }

    @Test
    void tryAcquireAllowsAgainOnceTheWindowHasPassed() {
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("client")).isTrue();
    }

    @Test
    void tryAcquireStillRefusesJustBeforeTheWindowEnds() {
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");

        clock.advance(Duration.ofSeconds(59));

        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void recordedEventsBlockTheKeyOnceTheLimitIsReached() {
        limiter.record("email");
        limiter.record("email");
        assertThat(limiter.isBlocked("email")).isFalse();

        limiter.record("email");

        assertThat(limiter.isBlocked("email")).isTrue();
        assertThat(limiter.isBlocked("other")).isFalse();
    }

    @Test
    void aBlockedKeyIsFreeAgainAfterTheWindow() {
        limiter.record("email");
        limiter.record("email");
        limiter.record("email");

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.isBlocked("email")).isFalse();
    }

    @Test
    void resetClearsTheCount() {
        limiter.record("email");
        limiter.record("email");
        limiter.record("email");

        limiter.reset("email");

        assertThat(limiter.isBlocked("email")).isFalse();
    }

    @Test
    void retryAfterReportsTheSecondsLeftInTheWindow() {
        limiter.tryAcquire("client");
        clock.advance(Duration.ofSeconds(20));

        assertThat(limiter.retryAfterSeconds("client")).isEqualTo(40);
    }

    @Test
    void retryAfterIsZeroForAKeyWithNoRecentEvents() {
        assertThat(limiter.retryAfterSeconds("unknown")).isZero();
    }

    @Test
    void manyDistinctKeysDoNotBreakTheLimiterAfterTheirWindowsExpire() {
        for (int i = 0; i < 10_050; i++) {
            limiter.tryAcquire("client-" + i);
        }

        clock.advance(Duration.ofMinutes(2));

        assertThat(limiter.tryAcquire("client-1")).isTrue();
        assertThat(limiter.tryAcquire("client-fresh")).isTrue();
    }
}
