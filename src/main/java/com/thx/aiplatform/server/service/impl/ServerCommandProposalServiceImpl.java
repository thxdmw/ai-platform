package com.thx.aiplatform.server.service.impl;

import com.thx.aiplatform.server.config.ServerAssistantProperties;
import com.thx.aiplatform.server.dto.ServerCommandRequest;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.enums.ServerCommandRisk;
import com.thx.aiplatform.server.service.ServerActionContinuationService;
import com.thx.aiplatform.server.service.ServerCommandProposalService;
import com.thx.aiplatform.server.service.ServerCommandRiskClassifier;
import com.thx.aiplatform.server.service.ServerCommandTemplateService;
import com.thx.aiplatform.server.service.ServerConfigurationService;
import com.thx.aiplatform.server.vo.PendingServerCommandProposalView;
import com.thx.aiplatform.server.vo.ServerCommandProposalResult;
import com.thx.aiplatform.server.vo.ServerCommandView;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「提议添加命令」实现：模型经 proposeCommand 工具提出一条命令 → 服务端生成一次性确认
 * 选项并自动分级风险 → 用户点击添加 → 服务端把命令落库并签发续跑凭证让模型继续原任务。
 *
 * <p>确认选项只存内存（重启失效的取舍与续跑凭证相同——数据库里保存的是确认后才落库的
 * 命令，待确认快照丢了最多让助手重新生成一次）。命令文本在这里统一校验与规范化，风险
 * 等级由服务端白名单分类器判定，不信任模型自报。</p>
 */
@Service
public class ServerCommandProposalServiceImpl implements ServerCommandProposalService {

    private static final int MAX_PENDING_PROPOSALS = 100;
    private final ServerConfigurationService configurationService;
    private final ServerCommandRiskClassifier riskClassifier;
    private final ServerAssistantProperties properties;
    private final ServerActionContinuationService continuationService;
    private final ServerCommandTemplateService templateService;
    private final Clock clock;
    private final Map<String, PendingProposal> proposals = new ConcurrentHashMap<>();

    @Autowired
    public ServerCommandProposalServiceImpl(ServerConfigurationService configurationService,
                                            ServerCommandRiskClassifier riskClassifier,
                                            ServerAssistantProperties properties,
                                            ServerActionContinuationService continuationService,
                                            ServerCommandTemplateService templateService) {
        this(configurationService, riskClassifier, properties, continuationService, templateService, Clock.systemUTC());
    }

    public ServerCommandProposalServiceImpl(ServerConfigurationService configurationService,
                                            ServerCommandRiskClassifier riskClassifier,
                                            ServerAssistantProperties properties,
                                            ServerActionContinuationService continuationService,
                                            ServerCommandTemplateService templateService, Clock clock) {
        this.configurationService = configurationService;
        this.riskClassifier = riskClassifier;
        this.properties = properties;
        this.continuationService = continuationService;
        this.templateService = templateService;
        this.clock = clock;
    }

    /**
     * 生成确认选项：先清除该对话已有的提议（同一对话同时只允许一个待确认提议，防止模型
     * 连续多次提出命令时页面出现互相覆盖的方案），再校验字段与风险。命令文本禁止 NUL
     * 字符——SSH 通道对 NUL 的处理不可靠，可能截断命令造成行为歧义。
     */
    @Override
    public PendingServerCommandProposalView prepare(String conversationId, ServerEntity server, String name,
                                             String description, String commandText, String parameterSchema,
                                             String reason) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (proposals.size() >= MAX_PENDING_PROPOSALS) {
            throw new IllegalStateException("待确认命令提议过多，请稍后重试");
        }
        String normalizedName = required(name, "命令名称", 80);
        String normalizedDescription = required(description, "命令用途", 500);
        String normalizedCommand = required(commandText, "命令内容", 8000);
        if (normalizedCommand.indexOf('\0') >= 0) throw new IllegalArgumentException("命令内容不能包含空字符");
        String normalizedSchema = templateService.normalizeSchema(normalizedCommand, parameterSchema);
        ServerCommandRisk risk = riskClassifier.classify(
                templateService.classificationText(normalizedCommand, normalizedSchema));
        String actionId = UUID.randomUUID().toString();
        PendingProposal proposal = new PendingProposal(actionId, conversationId, server, normalizedName,
                normalizedDescription, normalizedCommand, risk, normalizeReason(reason),
                normalizedSchema,
                clock.instant().plus(properties.getApprovalTtl()));
        proposals.put(actionId, proposal);
        return toView(proposal);
    }

    /**
     * 用户点击添加后消费提议并落库。synchronized 防止并发点击把同一提议写入两次；先落库
     * 成功再移除提议，保证「用户确认了但保存失败」时提议仍在，可以重试而不是丢失确认
     * 语义；保存成功后签发续跑凭证，让模型带着可信事件回到原任务。危险命令添加后仍需
     * 二次确认才可执行。
     */
    @Override
    public synchronized ServerCommandProposalResult approve(String actionId) {
        PendingProposal proposal = proposals.get(actionId);
        if (proposal == null) throw new IllegalArgumentException("命令添加选项不存在或已处理");
        if (!proposal.expiresAt().isAfter(clock.instant())) {
            proposals.remove(actionId);
            throw new IllegalArgumentException("命令添加选项已过期，请让助手重新生成");
        }
        ServerCommandView command = configurationService.createCommand(proposal.server().getId(),
                new ServerCommandRequest(proposal.name(), proposal.description(), proposal.commandText(),
                        proposal.parameterSchema(), proposal.risk().name(), true, 1000));
        proposals.remove(actionId);
        String riskMessage = proposal.risk() == ServerCommandRisk.DANGEROUS
                ? "该命令属于危险操作，执行时仍需再次确认" : "该命令后续可由助手直接执行";
        String continuationId = continuationService.prepare(proposal.conversationId(), proposal.server().getId(),
                "系统可信事件：用户已确认添加命令“" + command.name() + "”（命令 ID：" + command.id()
                        + "）。请继续完成用户原来的任务：先重新调用 listCommands 获取最新清单，再按命令 ID 执行。"
                        + "如果它属于危险命令，仍须生成执行确认选项，不得绕过二次确认。");
        return new ServerCommandProposalResult(actionId, true, "命令已添加到“" + proposal.server().getName()
                + "”；" + riskMessage, command, continuationId);
    }

    @Override
    public void cancel(String actionId) { proposals.remove(actionId); }

    @Override
    public void cancelForConversation(String conversationId) {
        proposals.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    @Override
    public PendingServerCommandProposalView findForConversation(String conversationId) {
        cleanupExpired();
        return proposals.values().stream().filter(value -> value.conversationId().equals(conversationId))
                .findFirst().map(this::toView).orElse(null);
    }

    private PendingServerCommandProposalView toView(PendingProposal proposal) {
        return new PendingServerCommandProposalView(proposal.actionId(), proposal.server().getId(),
                proposal.server().getName(), proposal.name(), proposal.description(), proposal.commandText(),
                proposal.parameterSchema(), proposal.risk().name(), proposal.reason(), proposal.expiresAt(),
                "PENDING_COMMAND_APPROVAL", "ADD_COMMAND");
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + "过长");
        return normalized;
    }

    /**
     * 模型的理由可能是一大段话，截断到 300 字符以内足够页面展示与留存，避免把任意长度的
     * 模型输出带进内存视图。
     */
    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "当前任务需要这条命令";
        String value = reason.trim();
        return value.substring(0, Math.min(value.length(), 300));
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        proposals.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record PendingProposal(String actionId, String conversationId, ServerEntity server, String name,
                                   String description, String commandText, ServerCommandRisk risk, String reason,
                                   String parameterSchema, Instant expiresAt) { }
}