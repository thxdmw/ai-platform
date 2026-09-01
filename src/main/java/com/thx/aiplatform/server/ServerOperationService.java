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
    private final SshCommandExecutor executor;
    private final ServerAssistantProperties properties;
    private final ServerActionContinuationService continuationService;
    private final Clock clock;
    private final Map<String, PendingOperation> operations = new ConcurrentHashMap<>();

    @Autowired
    ServerOperationService(SshCommandExecutor executor, ServerAssistantProperties properties,
                           ServerActionContinuationService continuationService) {
        this(executor, properties, continuationService, Clock.systemUTC());
    }

    ServerOperationService(SshCommandExecutor executor, ServerAssistantProperties properties,
                           ServerActionContinuationService continuationService, Clock clock) {
        this.executor = executor;
        this.properties = properties;
        this.continuationService = continuationService;
        this.clock = clock;
    }

    PendingServerOperationView prepare(String conversationId, ServerDefinition server,
                                       ServerCommandDefinition command, String reason) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (command.riskLevel() != ServerCommandRisk.DANGEROUS) {
            throw new IllegalArgumentException("普通命令不需要生成确认选项");
        }
        if (operations.size() >= MAX_PENDING_OPERATIONS) throw new IllegalStateException("待确认服务器操作过多，请稍后重试");
        String actionId = UUID.randomUUID().toString();
        PendingOperation pending = new PendingOperation(actionId, conversationId, server, command,
                normalizeReason(reason), clock.instant().plus(properties.getApprovalTtl()));
        operations.put(actionId, pending);
        return toView(pending);
    }

    ServerOperationResult approve(String actionId) {
        PendingOperation pending = operations.remove(actionId);
        if (pending == null) throw new IllegalArgumentException("服务器操作选项不存在或已处理");
        if (!pending.expiresAt().isAfter(clock.instant())) throw new IllegalArgumentException("服务器操作选项已过期，请重新生成");
        SshExecutionResult result;
        try {
            result = executor.execute(pending.server(), pending.command().commandText());
        } catch (RuntimeException exception) {
            // 网络中断时远端命令可能已经开始，不能自动重试同一危险操作。
            String continuationId = continuationService.prepare(pending.conversationId(), pending.server().id(),
                    "系统可信事件：用户已确认执行危险命令“" + pending.command().name()
                            + "”，但执行结果不确定：" + exception.getMessage()
                            + "。请明确提醒用户先核对服务器实际状态，不得自动重试该命令。");
            return new ServerOperationResult(actionId, false,
                    "执行结果不确定，请先核对服务器状态：" + exception.getMessage(), null, continuationId);
        }
        String continuationId = continuationService.prepare(pending.conversationId(), pending.server().id(),
                operationContinuationMessage(pending, result));
        return new ServerOperationResult(actionId, result.successful(),
                result.successful() ? "服务器命令执行成功" : "服务器命令执行失败，退出码 " + result.exitCode(),
                result, continuationId);
    }

    void cancel(String actionId) { operations.remove(actionId); }

    void cancelForConversation(String conversationId) {
        operations.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    void cancelForServer(String serverId) {
        operations.entrySet().removeIf(entry -> entry.getValue().server().id().equals(serverId));
    }

    PendingServerOperationView findForConversation(String conversationId) {
        cleanupExpired();
        return operations.values().stream().filter(value -> value.conversationId().equals(conversationId))
                .findFirst().map(this::toView).orElse(null);
    }

    private PendingServerOperationView toView(PendingOperation pending) {
        return new PendingServerOperationView(pending.actionId(), pending.server().id(), pending.server().name(),
                pending.command().id(), pending.command().name(), pending.command().commandText(), pending.reason(),
                pending.expiresAt(), "PENDING_APPROVAL", "EXECUTE_COMMAND");
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "用户要求执行该命令";
        String value = reason.trim();
        return value.substring(0, Math.min(value.length(), 300));
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        operations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String operationContinuationMessage(PendingOperation pending, SshExecutionResult result) {
        return "系统可信事件：用户已确认并执行危险命令“" + pending.command().name() + "”。\n"
                + result.forModel() + "\n请基于真实执行结果继续完成用户原来的任务并给出结论，"
                + "不得自动重试刚才的危险命令。";
    }

    private record PendingOperation(String actionId, String conversationId, ServerDefinition server,
                                    ServerCommandDefinition command, String reason, Instant expiresAt) { }
}
