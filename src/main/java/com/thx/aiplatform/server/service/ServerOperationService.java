package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.model.SshExecutionResult;
import com.thx.aiplatform.server.model.ServerOperationResult;
import com.thx.aiplatform.server.model.ServerDefinition;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.model.ServerCommandDefinition;
import com.thx.aiplatform.server.model.PendingServerOperationView;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 危险命令的二次确认服务：模型工具只生成「待确认操作」，用户点击确认后由 approve 真正
 * 执行。确认选项只存内存（重启失效的取舍同续跑凭证——这里只是待执行的瞬时快照，丢了
 * 重新生成即可），且只受理 DANGEROUS 级别的命令——普通命令直接执行，不需要也不可能
 * 伪装成待确认危险操作。
 */
@Service
public class ServerOperationService {

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

    /**
     * 生成待确认操作：普通命令直接执行、无需确认，这里只受理危险命令；同一对话同一时刻
     * 只允许一个待确认操作，先把旧的清掉，防止模型连续多次提出危险命令时页面出现多个
     * 确认框互相干扰。
     */
    public PendingServerOperationView prepare(String conversationId, ServerDefinition server,
                                       ServerCommandDefinition command, String reason) {
        return prepare(conversationId, server, command, command.commandText(), reason);
    }

    public PendingServerOperationView prepare(String conversationId, ServerDefinition server,
                                               ServerCommandDefinition command, String renderedCommand,
                                               String reason) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (command.riskLevel() != ServerCommandRisk.DANGEROUS) {
            throw new IllegalArgumentException("普通命令不需要生成确认选项");
        }
        if (operations.size() >= MAX_PENDING_OPERATIONS) throw new IllegalStateException("待确认服务器操作过多，请稍后重试");
        String actionId = UUID.randomUUID().toString();
        PendingOperation pending = new PendingOperation(actionId, conversationId, server, command, renderedCommand,
                normalizeReason(reason), clock.instant().plus(properties.getApprovalTtl()));
        operations.put(actionId, pending);
        return toView(pending);
    }

    /**
     * 用户确认后执行。先原子 remove 再执行：并发双击时只有一个请求能拿到操作，保证危险
     * 命令最多执行一次。执行失败（含网络中断）时不自动重试——远端可能已经收到并开始执行，
     * 重试会产生重复副作用——而是签发续跑凭证，让模型提醒用户先核对服务器实际状态。
     */
    public ServerOperationResult approve(String actionId) {
        PendingOperation pending = operations.remove(actionId);
        if (pending == null) throw new IllegalArgumentException("服务器操作选项不存在或已处理");
        if (!pending.expiresAt().isAfter(clock.instant())) throw new IllegalArgumentException("服务器操作选项已过期，请重新生成");
        SshExecutionResult result;
        try {
            result = executor.execute(pending.server(), pending.renderedCommand());
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

    public void cancel(String actionId) { operations.remove(actionId); }

    public void cancelForConversation(String conversationId) {
        operations.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    /**
     * 服务器配置变更或删除时调用：连接参数已变，残存的待确认操作必须作废。
     */
    void cancelForServer(String serverId) {
        operations.entrySet().removeIf(entry -> entry.getValue().server().id().equals(serverId));
    }

    /**
     * 供 SSE 流结束时查询该对话的待确认操作，由控制器推给页面展示确认按钮。
     */
    public PendingServerOperationView findForConversation(String conversationId) {
        cleanupExpired();
        return operations.values().stream().filter(value -> value.conversationId().equals(conversationId))
                .findFirst().map(this::toView).orElse(null);
    }

    private PendingServerOperationView toView(PendingOperation pending) {
        return new PendingServerOperationView(pending.actionId(), pending.server().id(), pending.server().name(),
                pending.command().id(), pending.command().name(), pending.renderedCommand(), pending.reason(),
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

    /**
     * 把执行结果打包成「系统可信事件」传给模型：工具输出默认不可信，但这里的结果是
     * 服务端从远端实际抓回的事实，模型应基于它下结论，且末尾明确禁止自动重试。
     */
    private String operationContinuationMessage(PendingOperation pending, SshExecutionResult result) {
        return "系统可信事件：用户已确认并执行危险命令“" + pending.command().name() + "”。\n"
                + result.forModel() + "\n请基于真实执行结果继续完成用户原来的任务并给出结论，"
                + "不得自动重试刚才的危险命令。";
    }

    private record PendingOperation(String actionId, String conversationId, ServerDefinition server,
                                    ServerCommandDefinition command, String renderedCommand,
                                    String reason, Instant expiresAt) { }
}
