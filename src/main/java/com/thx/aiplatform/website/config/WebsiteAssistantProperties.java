package com.thx.aiplatform.website.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 网站助手公开接口的安全与行为参数，绑定配置前缀 ai-platform.website。
 * <p>CORS 白名单、限流配额、SSE 超时同属「公开无鉴权接口」的关键防线参数，
 * 统一收敛在这一个类里而非散落各处，避免各环节各自读取造成口径不一致。</p>
 */
@ConfigurationProperties(prefix = "ai-platform.website")
public class WebsiteAssistantProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    private String accessToken = "";

    private int requestsPerMinute = 6;

    private int requestsPerClientPerDay = 30;

    private int requestsPerDay = 300;

    private Duration sseTimeout = Duration.ofSeconds(90);

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    // 防御性拷贝：防止配置源后续修改列表时意外改掉已生效的 CORS 白名单。
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public int getRequestsPerClientPerDay() {
        return requestsPerClientPerDay;
    }

    public void setRequestsPerClientPerDay(int requestsPerClientPerDay) {
        this.requestsPerClientPerDay = positive(requestsPerClientPerDay, "单客户端每日请求数");
    }

    public int getRequestsPerDay() {
        return requestsPerDay;
    }

    public void setRequestsPerDay(int requestsPerDay) {
        this.requestsPerDay = positive(requestsPerDay, "全站每日请求数");
    }

    // 配额为 0 或负数等于关停接口，几乎必然是配置笔误；在设置时就抛错（fail-fast），
    // 而不是让接口上线后静默拒掉所有请求再排查。
    public void setRequestsPerMinute(int requestsPerMinute) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("每分钟请求数必须大于 0");
        }
        this.requestsPerMinute = requestsPerMinute;
    }

    public Duration getSseTimeout() {
        return sseTimeout;
    }

    // SSE 超时不允许为 0 或负数：否则 SseEmitter 会立即超时，前端永远收不到完整响应。
    public void setSseTimeout(Duration sseTimeout) {
        if (sseTimeout == null || sseTimeout.isNegative() || sseTimeout.isZero()) {
            throw new IllegalArgumentException("SSE 超时时间必须大于 0");
        }
        this.sseTimeout = sseTimeout;
    }

    private int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + "必须大于 0");
        return value;
    }
}
