package com.thx.aiplatform.server.vo;

/**
 * 服务器的对外视图：不含任何凭据字段，hostKey 用于页面展示与确认；credentialConfigured
 * 指示该服务器是否已具备可用凭据（见 ServerConfigurationServiceImpl#toView，创建必填/更新留空
 * 保留密文，因此恒为 true）。
 */
public record ServerView(
        String id,
        String name,
        String host,
        int port,
        String username,
        String authenticationType,
        String hostKey,
        boolean enabled,
        boolean credentialConfigured
) { }
