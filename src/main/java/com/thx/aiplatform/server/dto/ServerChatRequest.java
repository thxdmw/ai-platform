package com.thx.aiplatform.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 对话请求体。conversationId 字符集被严格限定（字母数字下划线连字符、最长 64）——它会
 * 被用作平台侧记忆的键并出现在 URL 中，限制字符集能同时挡掉路径注入与存储键污染；
 * message 上限 4000 字，防止模型上下文被一次性塞进超大输入。
 */
public record ServerChatRequest(
        @NotBlank(message = "conversationId 不能为空")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "conversationId 格式不合法")
        String conversationId,
        @NotBlank(message = "请选择服务器")
        @Size(max = 36, message = "服务器编号不合法")
        String serverId,
        @NotBlank(message = "消息不能为空")
        @Size(max = 4000, message = "消息不能超过 4000 个字符")
        String message,
        @Size(max = 36, message = "模型编号不合法")
        String modelId,
        @Size(max = 20, message = "推理等级不合法")
        String reasoningEffort
) { }
