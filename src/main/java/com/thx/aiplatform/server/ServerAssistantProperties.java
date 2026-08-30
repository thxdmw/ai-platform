package com.thx.aiplatform.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-platform.server")
public class ServerAssistantProperties {

    private String accessToken = "";
    private String serversJson = "[]";
    private Duration connectTimeout = Duration.ofSeconds(8);
    private Duration commandTimeout = Duration.ofSeconds(30);
    private Duration sseTimeout = Duration.ofSeconds(120);
    private Duration approvalTtl = Duration.ofMinutes(10);
    private int maxOutputBytes = 16_384;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = trim(accessToken); }
    public String getServersJson() { return serversJson; }
    public void setServersJson(String serversJson) { this.serversJson = serversJson == null || serversJson.isBlank() ? "[]" : serversJson.trim(); }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { this.connectTimeout = positive(value, "SSH 连接超时"); }
    public Duration getCommandTimeout() { return commandTimeout; }
    public void setCommandTimeout(Duration value) { this.commandTimeout = positive(value, "SSH 命令超时"); }
    public Duration getSseTimeout() { return sseTimeout; }
    public void setSseTimeout(Duration value) { this.sseTimeout = positive(value, "服务器助手 SSE 超时"); }
    public Duration getApprovalTtl() { return approvalTtl; }
    public void setApprovalTtl(Duration value) { this.approvalTtl = positive(value, "服务器操作确认有效期"); }
    public int getMaxOutputBytes() { return maxOutputBytes; }
    public void setMaxOutputBytes(int value) {
        if (value < 1_024 || value > 1_048_576) throw new IllegalArgumentException("SSH 输出上限必须在 1024 到 1048576 字节之间");
        this.maxOutputBytes = value;
    }

    private Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + "必须大于 0");
        return value;
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
}
