package com.thx.aiplatform.website.model;

import java.time.LocalDateTime;

/** 网站助手对外展示与回答边界配置。 */
public record WebsiteAssistantSettings(
        String assistantName,
        String welcomeMessage,
        String promptAddition,
        boolean enabled,
        LocalDateTime updatedAt
) {
}
