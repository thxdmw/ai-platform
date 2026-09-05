package com.thx.aiplatform.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServerModelProviderRequest(
        @NotBlank(message = "Provider ID 不能为空")
        @jakarta.validation.constraints.Pattern(regexp = "[a-z][a-z0-9-]{1,63}", message = "Provider ID 必须以小写字母开头，只能包含小写字母、数字和连字符")
        String providerKey,
        @NotBlank(message = "提供方名称不能为空") @Size(max = 80) String name,
        @NotBlank(message = "基础地址不能为空") @Size(max = 500) String baseUrl,
        @NotBlank(message = "API 协议不能为空") @Size(max = 40) String apiProtocol,
        @Size(max = 4000) String apiKey,
        boolean enabled,
        @Valid @Size(max = 50, message = "单个提供方最多配置 50 个模型") List<ServerModelOptionRequest> models
) { }
