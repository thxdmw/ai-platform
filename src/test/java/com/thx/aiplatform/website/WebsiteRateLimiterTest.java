package com.thx.aiplatform.website;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WebsiteRateLimiterTest {

    @Test
    void 同一客户端超过窗口配额后拒绝请求() {
        WebsiteAssistantProperties properties = new WebsiteAssistantProperties();
        properties.setRequestsPerMinute(2);
        WebsiteRateLimiter limiter = new WebsiteRateLimiter(
                properties,
                Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(limiter.tryAcquire("127.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("127.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("127.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("127.0.0.2")).isTrue();
    }
}
