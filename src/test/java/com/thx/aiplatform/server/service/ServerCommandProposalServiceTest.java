package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.model.ServerDefinition;
import com.thx.aiplatform.server.model.ServerCommandView;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.model.ServerCommandProposalResult;
import com.thx.aiplatform.server.model.ServerAuthenticationType;
import com.thx.aiplatform.server.model.PendingServerCommandProposalView;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证命令提议的两次形态：确认前不落库、确认后只写入归属服务器且只能处理一次。 */
class ServerCommandProposalServiceTest {

    @Test
    void 用户确认前不保存且确认后只写入提议所属服务器() {
        ServerConfigurationService configurationService = mock(ServerConfigurationService.class);
        ServerCommandRiskClassifier classifier = mock(ServerCommandRiskClassifier.class);
        when(classifier.classify("systemctl status nginx --no-pager")).thenReturn(ServerCommandRisk.NORMAL);
        ServerCommandView saved = new ServerCommandView("command-1", "server-a", "查看 Nginx 状态",
                "查看服务状态", "systemctl status nginx --no-pager", "NORMAL", true, 1000);
        when(configurationService.createCommand(eq("server-a"), argThat(request ->
                request.name().equals("查看 Nginx 状态") && request.riskLevel().equals("NORMAL"))))
                .thenReturn(saved);
        ServerCommandProposalService service = service(configurationService, classifier);

        PendingServerCommandProposalView pending = service.prepare("conversation-1", server(),
                "查看 Nginx 状态", "查看服务状态", "systemctl status nginx --no-pager", "排查服务异常");

        verifyNoInteractions(configurationService);
        assertThat(pending.serverId()).isEqualTo("server-a");
        assertThat(pending.riskLevel()).isEqualTo("NORMAL");
        ServerCommandProposalResult result = service.approve(pending.actionId());
        assertThat(result.command()).isSameAs(saved);
        assertThat(result.continuationId()).isEqualTo("continuation-1");
        verify(configurationService).createCommand(eq("server-a"), argThat(request ->
                request.commandText().equals("systemctl status nginx --no-pager")));
    }

    @Test
    void 添加选项只能处理一次() {
        ServerConfigurationService configurationService = mock(ServerConfigurationService.class);
        ServerCommandRiskClassifier classifier = mock(ServerCommandRiskClassifier.class);
        when(classifier.classify("reboot")).thenReturn(ServerCommandRisk.DANGEROUS);
        when(configurationService.createCommand(eq("server-a"), argThat(request -> true)))
                .thenReturn(new ServerCommandView("command-2", "server-a", "重启服务器", "重启",
                        "reboot", "DANGEROUS", true, 1000));
        ServerCommandProposalService service = service(configurationService, classifier);
        PendingServerCommandProposalView pending = service.prepare("conversation-1", server(),
                "重启服务器", "重启", "reboot", "用户要求重启");

        service.approve(pending.actionId());

        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    private ServerCommandProposalService service(ServerConfigurationService configurationService,
                                                 ServerCommandRiskClassifier classifier) {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setApprovalTtl(Duration.ofMinutes(10));
        ServerActionContinuationService continuationService = mock(ServerActionContinuationService.class);
        when(continuationService.prepare(eq("conversation-1"), eq("server-a"), argThat(message ->
                message.contains("继续完成用户原来的任务")))).thenReturn("continuation-1");
        return new ServerCommandProposalService(configurationService, classifier, properties, continuationService,
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private ServerDefinition server() {
        return new ServerDefinition("server-a", "服务器 A", "host", 22, "ops", ServerAuthenticationType.PASSWORD,
                "ciphertext", null, "host ssh-ed25519 AAAATESTKEY", true);
    }
}
