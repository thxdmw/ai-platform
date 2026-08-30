package com.thx.aiplatform.website;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebsiteRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int CLEANUP_INTERVAL = 512;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();
    private final WebsiteAssistantProperties properties;
    private final Clock clock;

    @Autowired
    public WebsiteRateLimiter(WebsiteAssistantProperties properties) {
        this(properties, Clock.systemUTC());
    }

    WebsiteRateLimiter(WebsiteAssistantProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryAcquire(String clientId) {
        long currentWindow = clock.millis() / WINDOW_MILLIS;
        WindowCounter counter = counters.compute(clientId, (key, existing) -> {
            if (existing == null || existing.window() != currentWindow) {
                return new WindowCounter(currentWindow, 1);
            }
            return new WindowCounter(currentWindow, existing.count() + 1);
        });
        cleanupIfNecessary(currentWindow);
        return counter.count() <= properties.getRequestsPerMinute();
    }

    private void cleanupIfNecessary(long currentWindow) {
        if (requestsSinceCleanup.incrementAndGet() < CLEANUP_INTERVAL) {
            return;
        }
        requestsSinceCleanup.set(0);
        counters.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow - 1);
    }

    private record WindowCounter(long window, int count) {
    }
}
