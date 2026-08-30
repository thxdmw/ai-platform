package com.thx.aiplatform.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class ServerOperationService {

    private static final int MAX_PENDING_OPERATIONS = 100;
    private final ServerRegistry registry;
    private final SshCommandExecutor executor;
    private final ServerAssistantProperties properties;
    private final Clock clock;
    private final Map<String, PendingOperation> operations = new ConcurrentHashMap<>();

    @Autowired
    ServerOperationService(ServerRegistry registry, SshCommandExecutor executor, ServerAssistantProperties properties) {
        this(registry, executor, properties, Clock.systemUTC());
    }

    ServerOperationService(ServerRegistry registry, SshCommandExecutor executor,
                           ServerAssistantProperties properties, Clock clock) {
        this.registry = registry;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
    }

    PendingServerOperationView prepare(String conversationId, String serverId, String operation,
                                       String target, String reason) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (operations.size() >= MAX_PENDING_OPERATIONS) throw new IllegalStateException("待确认服务器操作过多，请稍后重试");
        ServerDefinition server = registry.require(serverId);
        ServerOperationType type = ServerOperationType.parse(operation);
        String normalizedTarget = ServerCommandCatalog.target(target);
        String command = switch (type) {
            case RESTART_SERVICE -> {
                if (!server.allowsService(normalizedTarget)) throw new IllegalArgumentException("服务不在操作白名单中：" + normalizedTarget);
                yield ServerCommandCatalog.restartService(normalizedTarget);
            }
            case RESTART_CONTAINER -> {
                if (!server.allowsContainer(normalizedTarget)) throw new IllegalArgumentException("容器不在操作白名单中：" + normalizedTarget);
                yield ServerCommandCatalog.restartContainer(normalizedTarget);
            }
        };
        String actionId = UUID.randomUUID().toString();
        PendingOperation pending = new PendingOperation(actionId, conversationId, server, type, normalizedTarget,
                command, normalizeReason(reason), clock.instant().plus(properties.getApprovalTtl()));
        operations.put(actionId, pending);
        return toView(pending);
    }

    ServerOperationResult approve(String actionId) {
        PendingOperation pending = operations.remove(actionId);
        if (pending == null) throw new IllegalArgumentException("服务器操作选项不存在或已处理");
        if (!pending.expiresAt().isAfter(clock.instant())) throw new IllegalArgumentException("服务器操作选项已过期，请重新生成");
        try {
            SshExecutionResult result = executor.execute(pending.server(), pending.command());
            return new ServerOperationResult(actionId, result.successful(),
                    result.successful() ? "服务器操作执行成功" : "服务器操作执行失败，退出码 " + result.exitCode(), result);
        } catch (RuntimeException exception) {
            // 操作请求已发送后不能安全重试，消费确认卡片并提示用户先核对服务器实际状态。
            return new ServerOperationResult(actionId, false,
                    "执行结果不确定，请先核对服务器状态：" + exception.getMessage(), null);
        }
    }

    void cancel(String actionId) { operations.remove(actionId); }

    void cancelForConversation(String conversationId) {
        operations.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    PendingServerOperationView findForConversation(String conversationId) {
        cleanupExpired();
        return operations.values().stream().filter(value -> value.conversationId().equals(conversationId))
                .findFirst().map(this::toView).orElse(null);
    }

    private PendingServerOperationView toView(PendingOperation pending) {
        return new PendingServerOperationView(pending.actionId(), pending.server().id(), pending.server().name(),
                pending.type().name(), pending.target(), pending.command(), pending.reason(), pending.expiresAt(), "PENDING_APPROVAL");
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "用户要求执行该操作";
        return reason.trim().substring(0, Math.min(reason.trim().length(), 300));
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        operations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record PendingOperation(String actionId, String conversationId, ServerDefinition server,
                                    ServerOperationType type, String target, String command,
                                    String reason, Instant expiresAt) { }
}
