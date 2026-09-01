package com.thx.aiplatform.blog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 博客助手的全部可调配置，集中于此并带默认值。setter 就地做校验与规范化，
 * 让错误的配置在启动绑定阶段立刻失败，而不是运行期第一次请求时才暴露。
 */
@ConfigurationProperties(prefix = "ai-platform.blog")
public class BlogAssistantProperties {

    private String accessToken = "";
    private String apiBaseUrl = "http://localhost:9090/agent/api/blog";
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration sseTimeout = Duration.ofSeconds(120);
    private Duration approvalTtl = Duration.ofMinutes(15);

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * 去掉末尾斜杠：URL 路径统一在 BlogApiClient 里拼接，否则配置带尾斜杠时会拼出双斜杠地址。
     */
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = requireText(apiBaseUrl, "博客 API 地址不能为空").replaceAll("/+$", "");
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "博客 API 建连超时");
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requirePositive(requestTimeout, "博客 API 请求超时");
    }

    public Duration getSseTimeout() {
        return sseTimeout;
    }

    public void setSseTimeout(Duration sseTimeout) {
        this.sseTimeout = requirePositive(sseTimeout, "博客助手 SSE 超时");
    }

    public Duration getApprovalTtl() {
        return approvalTtl;
    }

    public void setApprovalTtl(Duration approvalTtl) {
        this.approvalTtl = requirePositive(approvalTtl, "发布审批有效期");
    }

    private Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须大于 0");
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
