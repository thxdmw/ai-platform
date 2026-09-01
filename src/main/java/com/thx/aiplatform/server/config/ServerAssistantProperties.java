package com.thx.aiplatform.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 服务器助手配置绑定（前缀 ai-platform.server）。所有可调参数在这里集中校验并尽早失败：
 * 为 0 或负数的超时会在绑定阶段直接报错，而不是等运行时才暴露；maxOutputBytes 限定在
 * 1KB~1MB，过小没有实际意义，过大则让单条命令的输出有打爆内存的风险。
 */
@ConfigurationProperties(prefix = "ai-platform.server")
public class ServerAssistantProperties {

    private String accessToken = "";
    private String credentialMasterKey = "";
    private Duration connectTimeout = Duration.ofSeconds(8);
    private Duration commandTimeout = Duration.ofSeconds(30);
    private Duration sseTimeout = Duration.ofMinutes(10);
    private Duration approvalTtl = Duration.ofMinutes(10);
    private int maxOutputBytes = 16_384;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = trim(accessToken); }
    public String getCredentialMasterKey() { return credentialMasterKey; }
    public void setCredentialMasterKey(String value) { this.credentialMasterKey = trim(value); }
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
