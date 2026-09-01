package com.thx.aiplatform.server.model;

/**
 * 服务器的领域实体（库中存储形态）：凭据字段一律是密文（credentialCiphertext /
 * passphraseCiphertext），任何代码路径都不允许把明文塞进这个对象；与对外视图
 * {@link ServerView} 分离，视图不携带任何密文。SSH 执行器按此实体建立会话。
 */
public record ServerDefinition(
        String id,
        String name,
        String host,
        int port,
        String username,
        ServerAuthenticationType authenticationType,
        String credentialCiphertext,
        String passphraseCiphertext,
        String hostKey,
        boolean enabled
) { }
