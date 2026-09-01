package com.thx.aiplatform.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class ServerCommandProposalService {

    private static final int MAX_PENDING_PROPOSALS = 100;
    private final ServerConfigurationService configurationService;
    private final ServerCommandRiskClassifier riskClassifier;
    private final ServerAssistantProperties properties;
    private final Clock clock;
    private final Map<String, PendingProposal> proposals = new ConcurrentHashMap<>();

    @Autowired
    ServerCommandProposalService(ServerConfigurationService configurationService,
                                 ServerCommandRiskClassifier riskClassifier,
                                 ServerAssistantProperties properties) {
        this(configurationService, riskClassifier, properties, Clock.systemUTC());
    }

    ServerCommandProposalService(ServerConfigurationService configurationService,
                                 ServerCommandRiskClassifier riskClassifier,
                                 ServerAssistantProperties properties, Clock clock) {
        this.configurationService = configurationService;
        this.riskClassifier = riskClassifier;
        this.properties = properties;
        this.clock = clock;
    }

    PendingServerCommandProposalView prepare(String conversationId, ServerDefinition server, String name,
                                             String description, String commandText, String reason) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (proposals.size() >= MAX_PENDING_PROPOSALS) {
            throw new IllegalStateException("待确认命令提议过多，请稍后重试");
        }
        String normalizedName = required(name, "命令名称", 80);
        String normalizedDescription = required(description, "命令用途", 500);
        String normalizedCommand = required(commandText, "命令内容", 8000);
        if (normalizedCommand.indexOf('\0') >= 0) throw new IllegalArgumentException("命令内容不能包含空字符");
        ServerCommandRisk risk = riskClassifier.classify(normalizedCommand);
        String actionId = UUID.randomUUID().toString();
        PendingProposal proposal = new PendingProposal(actionId, conversationId, server, normalizedName,
                normalizedDescription, normalizedCommand, risk, normalizeReason(reason),
                clock.instant().plus(properties.getApprovalTtl()));
        proposals.put(actionId, proposal);
        return toView(proposal);
    }

    synchronized ServerCommandProposalResult approve(String actionId) {
        PendingProposal proposal = proposals.get(actionId);
        if (proposal == null) throw new IllegalArgumentException("命令添加选项不存在或已处理");
        if (!proposal.expiresAt().isAfter(clock.instant())) {
            proposals.remove(actionId);
            throw new IllegalArgumentException("命令添加选项已过期，请让助手重新生成");
        }
        ServerCommandView command = configurationService.createCommand(proposal.server().id(),
                new ServerCommandRequest(proposal.name(), proposal.description(), proposal.commandText(),
                        proposal.risk().name(), true, 1000));
        proposals.remove(actionId);
        String riskMessage = proposal.risk() == ServerCommandRisk.DANGEROUS
                ? "该命令属于危险操作，执行时仍需再次确认" : "该命令后续可由助手直接执行";
        return new ServerCommandProposalResult(actionId, true, "命令已添加到“" + proposal.server().name()
                + "”；" + riskMessage, command);
    }

    void cancel(String actionId) { proposals.remove(actionId); }

    void cancelForConversation(String conversationId) {
        proposals.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    PendingServerCommandProposalView findForConversation(String conversationId) {
        cleanupExpired();
        return proposals.values().stream().filter(value -> value.conversationId().equals(conversationId))
                .findFirst().map(this::toView).orElse(null);
    }

    private PendingServerCommandProposalView toView(PendingProposal proposal) {
        return new PendingServerCommandProposalView(proposal.actionId(), proposal.server().id(),
                proposal.server().name(), proposal.name(), proposal.description(), proposal.commandText(),
                proposal.risk().name(), proposal.reason(), proposal.expiresAt(),
                "PENDING_COMMAND_APPROVAL", "ADD_COMMAND");
    }

    private String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + "过长");
        return normalized;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "当前任务需要这条命令";
        String value = reason.trim();
        return value.substring(0, Math.min(value.length(), 300));
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        proposals.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record PendingProposal(String actionId, String conversationId, ServerDefinition server, String name,
                                   String description, String commandText, ServerCommandRisk risk, String reason,
                                   Instant expiresAt) { }
}
