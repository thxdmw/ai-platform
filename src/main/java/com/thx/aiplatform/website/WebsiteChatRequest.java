package com.thx.aiplatform.website;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WebsiteChatRequest(
        @NotBlank(message = "会话编号不能为空")
        @Size(max = 80, message = "会话编号过长")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "会话编号格式不正确")
        String conversationId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 500, message = "消息内容不能超过 500 个字符")
        String message
) {
}
