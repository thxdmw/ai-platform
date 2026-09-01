package com.thx.aiplatform.website.service;
import com.thx.aiplatform.website.config.WebsiteAssistantProperties;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 公开接口的 IP 限流：按 60 秒固定窗口计数，超过配额即拒绝。
 * <p>刻意做成「尽力而为」的粗粒度实现：固定窗口在窗口边界会产生约 2 倍瞬时报文，
 * 但换来的是只有一张 ConcurrentHashMap、无锁无调度线程的实现，足以挡住脚本刷量；
 * 精确的滑动窗口/令牌桶在这里属于过度设计——它与固定助手编号、CORS 白名单共同构成
 * 纵深防御，任何一层被突破都不至于裸奔。</p>
 */
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

    // compute 保证同 key 的读-改-写原子性：并发请求同时命中新窗口时不会各自从 1 起算导致
    // 计数丢失；窗口号由毫秒时间戳整除而来，窗口翻转自然换新计数，无需定时任务主动清旧 key。
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

    // 按请求次数惰性清理而不是跑定时任务，省掉一个调度线程；每 512 次清一次是
    // 「计数短暂虚高可接受、内存不随客户端数量无限增长」之间的取舍。
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
