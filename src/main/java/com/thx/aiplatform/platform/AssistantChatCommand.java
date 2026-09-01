package com.thx.aiplatform.platform;

import java.util.Objects;

/**
 * 平台核心对外暴露的对话指令契约：一次助手对话所需的全部入参。
 * <p>把它嵌套在 platform 模块而不是各助手模块内，是为了让「一次对话需要什么」的定义全局唯一，
 * 各助手（如 website）都经由它调用 {@link AssistantChatGateway}，避免各模块发明各自的入参结构。</p>
 */
public record AssistantChatCommand(
        String assistantId,
        String conversationId,
        String systemPrompt,
        String userMessage,
        AssistantModelConnection modelConnection
) {
    // 四字段缺一不可，任何来源构造的命令都要在入口拦下非法值（fail-fast）：
    // assistantId/conversationId 决定记忆隔离与上下文，systemPrompt 约束模型行为，userMessage 是用户输入。
    public AssistantChatCommand {
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(systemPrompt, "systemPrompt 不能为空");
        Objects.requireNonNull(userMessage, "userMessage 不能为空");
    }

    public AssistantChatCommand(String assistantId, String conversationId, String systemPrompt, String userMessage) {
        this(assistantId, conversationId, systemPrompt, userMessage, null);
    }
}
