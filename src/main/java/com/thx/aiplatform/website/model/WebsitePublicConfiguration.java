package com.thx.aiplatform.website.model;

/** 公开组件启动时可读取的无敏感配置。 */
public record WebsitePublicConfiguration(
        String assistantName,
        String welcomeMessage,
        boolean enabled
) {
}
