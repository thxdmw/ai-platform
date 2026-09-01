package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.repository.ServerConfigurationRepository;
import com.thx.aiplatform.server.model.ServerView;
import com.thx.aiplatform.server.model.ServerDefinition;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 服务器注册表：集中提供「按 ID 取服务器」与「列表视图」的读取入口，控制器与内部服务
 * 共用同一条查询路径和同一套「不存在即抛错、停用即拒绝」的语义，避免各层各自写查询
 * 导致错误信息与行为不一致。
 */
@Component
public class ServerRegistry {

    private final ServerConfigurationRepository repository;

    ServerRegistry(ServerConfigurationRepository repository) { this.repository = repository; }

    List<ServerView> views() { return repository.findServers(false).stream().map(this::toView).toList(); }

    List<ServerView> enabledViews() { return repository.findServers(true).stream().map(this::toView).toList(); }

    /**
     * 统一的「不存在即抛错」入口，所有按 ID 取服务器的调用都走这里，保证错误口径一致。
     */
    ServerDefinition require(String serverId) {
        return repository.findServer(serverId).orElseThrow(() -> new IllegalArgumentException("服务器不存在：" + serverId));
    }

    /**
     * 要求服务器必须处于启用状态：停用的服务器不能被对话框选作执行目标。
     */
    ServerDefinition requireEnabled(String serverId) {
        ServerDefinition server = require(serverId);
        if (!server.enabled()) throw new IllegalArgumentException("服务器已停用：" + server.name());
        return server;
    }

    /**
     * 领域实体转对外视图：凭据密文字段绝不透出。credentialConfigured 恒为 true，因为创建
     * 时凭据必填、更新时留空则保留原密文，库中服务器必然持有凭据，前端可放心依赖该标志。
     */
    ServerView toView(ServerDefinition server) {
        return new ServerView(server.id(), server.name(), server.host(), server.port(), server.username(),
                server.authenticationType().name(), server.hostKey(), server.enabled(), true);
    }
}
