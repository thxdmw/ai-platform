package com.thx.aiplatform.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class ServerRegistry {

    private final Map<String, ServerDefinition> servers;

    ServerRegistry(ServerAssistantProperties properties, ObjectMapper objectMapper) {
        try {
            List<ServerDefinition> definitions = objectMapper.readValue(
                    properties.getServersJson(), new TypeReference<List<ServerDefinition>>() { });
            Map<String, ServerDefinition> parsed = new LinkedHashMap<>();
            for (ServerDefinition raw : definitions) {
                ServerDefinition server = raw.normalized();
                if (!server.enabled()) continue;
                if (parsed.putIfAbsent(server.id(), server) != null) {
                    throw new IllegalArgumentException("服务器 ID 重复：" + server.id());
                }
            }
            this.servers = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (Exception exception) {
            throw new IllegalStateException("SERVER_ASSISTANT_SERVERS_JSON 配置不合法：" + exception.getMessage(), exception);
        }
    }

    List<ServerView> views() { return servers.values().stream().map(this::toView).toList(); }

    ServerDefinition require(String serverId) {
        ServerDefinition server = servers.get(serverId);
        if (server == null) throw new IllegalArgumentException("服务器不存在或未启用：" + serverId);
        return server;
    }

    private ServerView toView(ServerDefinition server) {
        return new ServerView(server.id(), server.name(), server.host(), server.port(), server.username(),
                server.allowedServices(), server.allowedContainers());
    }
}
