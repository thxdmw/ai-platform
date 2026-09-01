package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.model.SshExecutionResult;
import com.thx.aiplatform.server.model.ServerOperationResult;
import com.thx.aiplatform.server.model.ServerOperationDecisionRequest;
import com.thx.aiplatform.server.model.ServerOperationDecisionResult;
import com.thx.aiplatform.server.model.ServerDefinition;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.model.ServerCommandDefinition;
import com.thx.aiplatform.server.model.PendingServerOperationView;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
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

    private static final Logger log = LoggerFactory.getLogger(ServerOperationService.class);
    private static final int MAX_PENDING_OPERATIONS = 100;
    private static final int MAX_TRUSTED_EXACT_PER_CONVERSATION = 32;
    private final SshCommandExecutor executor;
    private final ServerAssistantProperties properties;
    private final ServerActionContinuationService continuationService;
    private final Clock clock;
    private final Map<String, PendingOperation> operations = new ConcurrentHashMap<>();
    private final Map<String, Set<TrustedExactCommand>> trustedExactCommands = new ConcurrentHashMap<>();

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
                null, false, normalizeReason(reason), clock.instant().plus(properties.getApprovalTtl()));
        operations.put(actionId, pending);
        return toView(pending);
    }

    /**
     * 临时命令只在无法证明只读时进入这里。commandText 保留给页面复核，renderedCommand 是
     * 已安全引用工作目录后的实际执行快照；二者分开保存，审批后绝不重新读取模型参数。
     */
    PendingServerOperationView prepareTemporary(String conversationId, ServerDefinition server,
                                                String workingDirectory, String commandText,
                                                String renderedCommand, String reason) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (operations.size() >= MAX_PENDING_OPERATIONS) {
            throw new IllegalStateException("待确认服务器操作过多，请稍后重试");
        }
        String actionId = UUID.randomUUID().toString();
        ServerCommandDefinition command = new ServerCommandDefinition(
                "temporary", server.id(), "临时 Shell 命令", "仅用于当前任务，不写入固定命令列表",
                commandText, "[]", ServerCommandRisk.DANGEROUS, true, 0);
        PendingOperation pending = new PendingOperation(actionId, conversationId, server, command, renderedCommand,
                workingDirectory, true, normalizeReason(reason), clock.instant().plus(properties.getApprovalTtl()));
        operations.put(actionId, pending);
        return toView(pending);
    }

    /**
     * 用户确认后执行。先原子 remove 再执行：并发双击时只有一个请求能拿到操作，保证危险
     * 命令最多执行一次。执行失败（含网络中断）时不自动重试——远端可能已经收到并开始执行，
     * 重试会产生重复副作用——而是签发续跑凭证，让模型提醒用户先核对服务器实际状态。
     */
    public ServerOperationResult approve(String actionId) {
        PendingOperation pending = consume(actionId);
        return execute(pending, false);
    }

    /** 临时命令支持单次执行、执行并记住完全相同命令，以及拒绝后带补充说明继续任务。 */
    public ServerOperationDecisionResult decide(String actionId, ServerOperationDecisionRequest request) {
        if ("REJECT_WITH_FEEDBACK".equals(request.decision())
                && (request.feedback() == null || request.feedback().isBlank())) {
            throw new IllegalArgumentException("请填写补充说明");
        }
        PendingOperation pending = consume(actionId);
        log.info("用户处理临时命令审批，conversationId={}，serverId={}，actionId={}，decision={}",
                pending.conversationId(), pending.server().id(), pending.actionId(), request.decision());
        return switch (request.decision()) {
            case "EXECUTE_ONCE" -> toDecisionResult(execute(pending, false), "EXECUTED");
            case "EXECUTE_AND_REMEMBER" -> {
                if (!pending.temporary()) throw new IllegalArgumentException("固定命令不能使用临时命令放行规则");
                ServerOperationResult result = execute(pending, true);
                yield toDecisionResult(result, result.success() ? "EXECUTED_AND_REMEMBERED" : "FAILED");
            }
            case "REJECT_WITH_FEEDBACK" -> rejectWithFeedback(pending, request.feedback());
            default -> throw new IllegalArgumentException("处理方式不合法");
        };
    }

    private ServerOperationResult execute(PendingOperation pending, boolean rememberExact) {
        long startedAt = System.nanoTime();
        log.info("服务器审批命令开始执行，conversationId={}，serverId={}，actionId={}，temporary={}，rememberExact={}",
                pending.conversationId(), pending.server().id(), pending.actionId(), pending.temporary(), rememberExact);
        SshExecutionResult result;
        try {
            result = executor.execute(pending.server(), pending.renderedCommand());
        } catch (RuntimeException exception) {
            // 网络中断时远端命令可能已经开始，不能自动重试同一危险操作。
            log.warn("服务器审批命令结果不确定，conversationId={}，serverId={}，actionId={}，耗时={}ms",
                    pending.conversationId(), pending.server().id(), pending.actionId(),
                    java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            String continuationId = continuationService.prepare(pending.conversationId(), pending.server().id(),
                    "系统可信事件：用户已确认执行危险命令“" + pending.command().name()
                            + "”，但执行结果不确定：" + exception.getMessage()
                            + "。请明确提醒用户先核对服务器实际状态，不得自动重试该命令。");
            return new ServerOperationResult(pending.actionId(), false,
                    "执行结果不确定，请先核对服务器状态：" + exception.getMessage(), null, continuationId);
        }
        if (rememberExact && result.successful()) {
            rememberExact(pending);
        }
        String continuationId = continuationService.prepare(pending.conversationId(), pending.server().id(),
                operationContinuationMessage(pending, result));
        log.info("服务器审批命令执行完成，conversationId={}，serverId={}，actionId={}，success={}，耗时={}ms",
                pending.conversationId(), pending.server().id(), pending.actionId(), result.successful(),
                java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return new ServerOperationResult(pending.actionId(), result.successful(),
                result.successful() ? "服务器命令执行成功" : "服务器命令执行失败，退出码 " + result.exitCode(),
                result, continuationId);
    }

    boolean isTrustedExact(String conversationId, String serverId, String workingDirectory, String commandText) {
        return trustedExactCommands.getOrDefault(conversationId, Set.of())
                .contains(new TrustedExactCommand(serverId, workingDirectory, commandText));
    }

    private PendingOperation consume(String actionId) {
        PendingOperation pending = operations.remove(actionId);
        if (pending == null) throw new IllegalArgumentException("服务器操作选项不存在或已处理");
        if (!pending.expiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("服务器操作选项已过期，请重新生成");
        }
        return pending;
    }

    private ServerOperationDecisionResult rejectWithFeedback(PendingOperation pending, String feedback) {
        String normalized = feedback == null ? "" : feedback.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("请填写补充说明");
        normalized = normalized.substring(0, Math.min(normalized.length(), 1000));
        String continuationId = continuationService.prepare(pending.conversationId(), pending.server().id(),
                "系统可信事件：用户拒绝执行临时命令“" + pending.command().commandText() + "”。\n"
                        + "用户补充说明：" + normalized + "\n"
                        + "不得执行已拒绝的命令；请根据补充说明继续原任务，必要时提出新的最小化操作。");
        return new ServerOperationDecisionResult(pending.actionId(), "REVISED", "已记录补充说明，任务将继续",
                null, continuationId);
    }

    /** 对话级精确放行也必须有界，避免长会话不断批准命令导致内存集合无限增长。 */
    private void rememberExact(PendingOperation pending) {
        Set<TrustedExactCommand> trusted = trustedExactCommands.computeIfAbsent(
                pending.conversationId(), ignored -> ConcurrentHashMap.newKeySet());
        if (trusted.size() >= MAX_TRUSTED_EXACT_PER_CONVERSATION) {
            trusted.stream().findFirst().ifPresent(trusted::remove);
        }
        trusted.add(new TrustedExactCommand(pending.server().id(), pending.workingDirectory(),
                pending.command().commandText()));
    }

    private ServerOperationDecisionResult toDecisionResult(ServerOperationResult result, String successStatus) {
        return new ServerOperationDecisionResult(result.actionId(), result.success() ? successStatus : "FAILED",
                result.message(), result.execution(), result.continuationId());
    }

    public void cancel(String actionId) { operations.remove(actionId); }

    public void cancelForConversation(String conversationId) {
        operations.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    /** 删除对话时连同本对话的精确放行记录一并清理；新消息只取消旧卡片，不清放行记录。 */
    public void forgetConversation(String conversationId) {
        cancelForConversation(conversationId);
        trustedExactCommands.remove(conversationId);
    }

    /**
     * 服务器配置变更或删除时调用：连接参数已变，残存的待确认操作必须作废。
     */
    void cancelForServer(String serverId) {
        operations.entrySet().removeIf(entry -> entry.getValue().server().id().equals(serverId));
        trustedExactCommands.values().forEach(values -> values.removeIf(value -> value.serverId().equals(serverId)));
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
                pending.command().id(), pending.command().name(),
                pending.temporary() ? pending.command().commandText() : pending.renderedCommand(),
                pending.workingDirectory(), pending.temporary(), pending.reason(), pending.expiresAt(),
                "PENDING_APPROVAL", pending.temporary() ? "EXECUTE_TEMPORARY_COMMAND" : "EXECUTE_COMMAND");
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
                                    String workingDirectory, boolean temporary,
                                    String reason, Instant expiresAt) { }

    private record TrustedExactCommand(String serverId, String workingDirectory, String commandText) { }
}
