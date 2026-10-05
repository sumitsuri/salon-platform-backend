package com.salonplatform.google;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Spaces out Google Places calls to reduce 429 bursts during Local Spotlight sync.
 */
@Component
@RequiredArgsConstructor
public class GooglePlacesRequestPacer {

    private final GooglePlacesProperties properties;
    private final Object lock = new Object();
    private long lastRequestAtMs;

    public void paceBeforeRequest() {
        long minInterval = properties.getMinIntervalBetweenRequestsMs();
        if (minInterval <= 0) {
            return;
        }
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long waitMs = minInterval - (now - lastRequestAtMs);
            if (waitMs > 0) {
                sleepQuietly(waitMs);
            }
            lastRequestAtMs = System.currentTimeMillis();
        }
    }

    public long backoffMsFor429(int attemptZeroBased) {
        long base = Math.max(500L, properties.getRateLimitBackoffMs());
        long capped = base * (1L << Math.min(attemptZeroBased, 4));
        return Math.min(capped, 30_000L);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
