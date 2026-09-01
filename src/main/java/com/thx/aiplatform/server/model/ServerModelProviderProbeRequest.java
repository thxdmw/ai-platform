package com.thx.aiplatform.server.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 测试未保存或已保存提供方时使用的临时连接信息，密钥不会持久化。 */
public record ServerModelProviderProbeRequest(
        @Size(max = 36) String providerId,
        @NotBlank(message = "API 地址不能为空") @Size(max = 500) String baseUrl,
        @NotBlank(message = "API 协议不能为空") @Size(max = 40) String apiProtocol,
        @Size(max = 4000) String apiKey
) { }
