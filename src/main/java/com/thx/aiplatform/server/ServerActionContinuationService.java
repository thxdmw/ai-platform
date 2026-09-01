package com.thx.aiplatform.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class ServerActionContinuationService {

    private static final int MAX_CONTINUATIONS = 100;
    private final ServerAssistantProperties properties;
    private final Clock clock;
    private final Map<String, Continuation> continuations = new ConcurrentHashMap<>();

    @Autowired
    ServerActionContinuationService(ServerAssistantProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ServerActionContinuationService(ServerAssistantProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String prepare(String conversationId, String serverId, String message) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (continuations.size() >= MAX_CONTINUATIONS) {
            continuations.entrySet().stream()
                    .min(Map.Entry.comparingByValue((left, right) -> left.expiresAt().compareTo(right.expiresAt())))
                    .ifPresent(entry -> continuations.remove(entry.getKey(), entry.getValue()));
        }
        String id = UUID.randomUUID().toString();
        continuations.put(id, new Continuation(conversationId, serverId, message,
                clock.instant().plus(properties.getApprovalTtl())));
        return id;
    }

    String consume(String id, String conversationId, String serverId) {
        Continuation continuation = continuations.get(id);
        if (continuation == null) throw new IllegalArgumentException("对话续跑凭证不存在或已使用");
        if (!continuation.expiresAt().isAfter(clock.instant())) {
            continuations.remove(id, continuation);
            throw new IllegalArgumentException("对话续跑凭证已过期，请重新发起请求");
        }
        if (!continuation.conversationId().equals(conversationId) || !continuation.serverId().equals(serverId)) {
            throw new IllegalArgumentException("对话续跑凭证与当前会话不匹配");
        }
        if (!continuations.remove(id, continuation)) {
            throw new IllegalArgumentException("对话续跑凭证不存在或已使用");
        }
        return continuation.message();
    }

    void cancelForConversation(String conversationId) {
        continuations.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        continuations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Continuation(String conversationId, String serverId, String message, Instant expiresAt) { }
}
