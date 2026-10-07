package com.mock.taskmanager.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RateLimiter {

    private static final int EVICTION_THRESHOLD = 10_000;

    private final int maxEvents;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxEvents, Duration window, Clock clock) {
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        AtomicBoolean allowed = new AtomicBoolean();
        windows.compute(key, (ignored, existing) -> {
            Window current = live(existing, now);
            if (current.count() >= maxEvents) {
                allowed.set(false);
                return current;
            }
            allowed.set(true);
            return current.increment();
        });
        evictExpired(now);
        return allowed.get();
    }

    public boolean isBlocked(String key) {
        Window current = live(windows.get(key), clock.instant());
        return current.count() >= maxEvents;
    }

    public void record(String key) {
        Instant now = clock.instant();
        windows.compute(key, (ignored, existing) -> live(existing, now).increment());
        evictExpired(now);
    }

    public void reset(String key) {
        windows.remove(key);
    }

    public long retryAfterSeconds(String key) {
        Instant now = clock.instant();
        Window current = windows.get(key);
        if (current == null || isExpired(current, now)) {
            return 0;
        }
        Duration remaining = Duration.between(now, current.start().plus(window));
        return Math.max(1, remaining.toSeconds() + (remaining.toNanosPart() > 0 ? 1 : 0));
    }

    private Window live(Window existing, Instant now) {
        return existing == null || isExpired(existing, now) ? new Window(now, 0) : existing;
    }

    private boolean isExpired(Window candidate, Instant now) {
        return !now.isBefore(candidate.start().plus(window));
    }

    private void evictExpired(Instant now) {
        if (windows.size() > EVICTION_THRESHOLD) {
            windows.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        }
    }

    private record Window(Instant start, int count) {

        Window increment() {
            return new Window(start, count + 1);
        }
    }
}
