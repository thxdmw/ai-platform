package com.thx.aiplatform.website.service;
import com.thx.aiplatform.website.config.WebsiteAssistantProperties;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流器测试：用固定 Clock 模拟时间，验证窗口内配额、超配额拒绝与不同客户端互不影响。
 */
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
