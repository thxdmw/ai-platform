package com.thx.aiplatform.blog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BlogChatRequest(
        @NotBlank(message = "conversationId 不能为空")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "conversationId 格式不合法")
        String conversationId,

        @NotBlank(message = "消息不能为空")
        @Size(max = 4000, message = "消息不能超过 4000 个字符")
        String message
) {
}
