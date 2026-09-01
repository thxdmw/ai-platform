package com.thx.aiplatform.blog.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 聊天请求体。conversationId 被用作会话级键（绑定工具、会话级清理）并出现在日志中，
 * 因此限定为字母数字与 -_，避免特殊字符带来匹配歧义和日志污染。
 */
public record BlogChatRequest(
        @NotBlank(message = "conversationId 不能为空")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "conversationId 格式不合法")
        String conversationId,

        @NotBlank(message = "消息不能为空")
        @Size(max = 4000, message = "消息不能超过 4000 个字符")
        String message
) {
}
