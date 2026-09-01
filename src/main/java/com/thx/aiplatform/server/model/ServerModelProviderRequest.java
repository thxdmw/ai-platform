package com.thx.aiplatform.server.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServerModelProviderRequest(
        @NotBlank(message = "提供方名称不能为空") @Size(max = 80) String name,
        @NotBlank(message = "基础地址不能为空") @Size(max = 500) String baseUrl,
        @NotBlank(message = "对话接口路径不能为空") @Size(max = 200) String chatCompletionsPath,
        @Size(max = 4000) String apiKey,
        boolean enabled,
        @Valid @Size(max = 50, message = "单个提供方最多配置 50 个模型") List<ServerModelOptionRequest> models
) { }
