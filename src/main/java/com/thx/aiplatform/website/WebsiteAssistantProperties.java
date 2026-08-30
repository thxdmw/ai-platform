package com.thx.aiplatform.website;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai-platform.website")
public class WebsiteAssistantProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    private int requestsPerMinute = 20;

    private Duration sseTimeout = Duration.ofSeconds(90);

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("每分钟请求数必须大于 0");
        }
        this.requestsPerMinute = requestsPerMinute;
    }

    public Duration getSseTimeout() {
        return sseTimeout;
    }

    public void setSseTimeout(Duration sseTimeout) {
        if (sseTimeout == null || sseTimeout.isNegative() || sseTimeout.isZero()) {
            throw new IllegalArgumentException("SSE 超时时间必须大于 0");
        }
        this.sseTimeout = sseTimeout;
    }
}
