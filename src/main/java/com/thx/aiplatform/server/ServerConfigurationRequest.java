package com.thx.aiplatform.server;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServerConfigurationRequest(
        @NotBlank(message = "服务器名称不能为空") @Size(max = 80, message = "服务器名称最多 80 个字符") String name,
        @NotBlank(message = "服务器地址不能为空") @Size(max = 255, message = "服务器地址最多 255 个字符") String host,
        @Min(value = 1, message = "SSH 端口不合法") @Max(value = 65535, message = "SSH 端口不合法") Integer port,
        @NotBlank(message = "SSH 用户名不能为空") @Size(max = 128, message = "SSH 用户名最多 128 个字符") String username,
        @NotBlank(message = "认证方式不能为空") String authenticationType,
        @Size(max = 20000, message = "SSH 凭据内容过长") String credential,
        @Size(max = 1000, message = "私钥口令过长") String privateKeyPassphrase,
        @NotBlank(message = "SSH 主机公钥不能为空") @Size(max = 4096, message = "SSH 主机公钥内容过长") String hostKey,
        Boolean enabled
) { }
