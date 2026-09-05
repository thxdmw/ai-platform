package com.thx.aiplatform.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 对话续跑请求体：携带页面动作产生的续跑凭证 ID，服务端校验凭证对应的会话与服务器
 * 一致后才允许恢复模型会话（凭证本身 36 位以内，防止把任意长字符串塞进 URL）。
 */
public record ServerContinuationRequest(
        @NotBlank(message = "conversationId 不能为空")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "conversationId 格式不合法")
        String conversationId,
        @NotBlank(message = "请选择服务器")
        @Size(max = 36, message = "服务器编号不合法")
        String serverId,
        @NotBlank(message = "续跑凭证不能为空")
        @Size(max = 36, message = "续跑凭证不合法")
        String continuationId,
        @Size(max = 36, message = "模型编号不合法")
        String modelId,
        @Size(max = 20, message = "推理等级不合法")
        String reasoningEffort
) { }
