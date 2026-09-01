package com.thx.aiplatform.server;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ServerRegistry {

    private final ServerConfigurationRepository repository;

    ServerRegistry(ServerConfigurationRepository repository) { this.repository = repository; }

    List<ServerView> views() { return repository.findServers(false).stream().map(this::toView).toList(); }

    List<ServerView> enabledViews() { return repository.findServers(true).stream().map(this::toView).toList(); }

    ServerDefinition require(String serverId) {
        return repository.findServer(serverId).orElseThrow(() -> new IllegalArgumentException("服务器不存在：" + serverId));
    }

    ServerDefinition requireEnabled(String serverId) {
        ServerDefinition server = require(serverId);
        if (!server.enabled()) throw new IllegalArgumentException("服务器已停用：" + server.name());
        return server;
    }

    ServerView toView(ServerDefinition server) {
        return new ServerView(server.id(), server.name(), server.host(), server.port(), server.username(),
                server.authenticationType().name(), server.hostKey(), server.enabled(), true);
    }
}
