package com.thx.aiplatform.platform;

import java.util.Objects;

public record AssistantChatCommand(
        String assistantId,
        String conversationId,
        String systemPrompt,
        String userMessage
) {
    public AssistantChatCommand {
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(systemPrompt, "systemPrompt 不能为空");
        Objects.requireNonNull(userMessage, "userMessage 不能为空");
    }
}
