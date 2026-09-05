package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.model.SshExecutionResult;
import com.thx.aiplatform.server.vo.ServerOperationResult;
import com.thx.aiplatform.server.dto.ServerOperationDecisionRequest;
import com.thx.aiplatform.server.vo.ServerOperationDecisionResult;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.model.ServerAuthenticationType;
import com.thx.aiplatform.server.vo.PendingServerOperationView;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证危险命令二次确认闭环：普通命令不能伪装确认、确认后只执行一次、连接异常转向核对实际状态。 */
class ServerOperationServiceTest {

    @Test
    void 危险命令必须经过确认且只能执行一次() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerEntity server = server();
        ServerCommandEntity command = dangerousCommand();
        when(executor.execute(same(server), eq(command.getCommandText())))
                .thenReturn(new SshExecutionResult("server-a", 0, "active", "", 120, false));
        ServerActionContinuationService continuationService = continuationService();
        ServerOperationService service = new ServerOperationService(executor, properties(), continuationService,
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        PendingServerOperationView pending = service.prepare("conversation-1", server, command, "服务异常");
        verifyNoInteractions(executor);

        ServerOperationResult result = service.approve(pending.actionId());
        assertThat(result.success()).isTrue();
        assertThat(result.continuationId()).isEqualTo("continuation-1");
        verify(executor).execute(same(server), eq(command.getCommandText()));
        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    @Test
    void 普通命令不能伪装成待确认危险操作() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerOperationService service = new ServerOperationService(
                executor, properties(), continuationService(), Clock.systemUTC());
        ServerCommandEntity command = new ServerCommandEntity(
                "command-2", "server-a", "查看状态", "查看状态", "uptime", "[]", ServerCommandRisk.NORMAL, true, 0);

        assertThatThrownBy(() -> service.prepare("conversation-1", server(), command, "检查"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不需要");
        verifyNoInteractions(executor);
    }

    @Test
    void 连接异常时消费操作并提示先核对实际状态() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerEntity server = server();
        ServerCommandEntity command = dangerousCommand();
        when(executor.execute(same(server), eq(command.getCommandText())))
                .thenThrow(new IllegalStateException("连接中断"));
        ServerOperationService service = new ServerOperationService(executor, properties(), continuationService(),
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        PendingServerOperationView pending = service.prepare("conversation-1", server, command, "服务异常");
        ServerOperationResult result = service.approve(pending.actionId());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("结果不确定").contains("连接中断");
        assertThat(result.execution()).isNull();
        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    @Test
    void 拒绝临时命令并补充说明后续跑同一任务() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerActionContinuationService continuationService = continuationService();
        ServerOperationService service = new ServerOperationService(executor, properties(), continuationService,
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));
        PendingServerOperationView pending = service.prepareTemporary(
                "conversation-1", server(), "/srv/app", "rm -f cache.tmp",
                "cd -- '/srv/app' && rm -f cache.tmp", "清理缓存");

        ServerOperationDecisionResult result = service.decide(pending.actionId(),
                new ServerOperationDecisionRequest("REJECT_WITH_FEEDBACK", "不要删除，先查看文件大小"));

        assertThat(result.status()).isEqualTo("REVISED");
        assertThat(result.continuationId()).isEqualTo("continuation-1");
        verifyNoInteractions(executor);
        verify(continuationService).prepare(eq("conversation-1"), eq("server-a"),
                argThat(message -> message.contains("先查看文件大小") && message.contains("不得执行已拒绝")));
    }

    @Test
    void 仅在临时命令成功后记住完全相同的命令和目录() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerEntity server = server();
        when(executor.execute(same(server), eq("cd -- '/srv/app' && systemctl restart api")))
                .thenReturn(new SshExecutionResult("server-a", 0, "", "", 50, false));
        ServerOperationService service = new ServerOperationService(executor, properties(), continuationService(),
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));
        PendingServerOperationView pending = service.prepareTemporary(
                "conversation-1", server, "/srv/app", "systemctl restart api",
                "cd -- '/srv/app' && systemctl restart api", "重启服务");

        ServerOperationDecisionResult result = service.decide(pending.actionId(),
                new ServerOperationDecisionRequest("EXECUTE_AND_REMEMBER", null));

        assertThat(result.status()).isEqualTo("EXECUTED_AND_REMEMBERED");
        assertThat(service.isTrustedExact("conversation-1", "server-a", "/srv/app", "systemctl restart api")).isTrue();
        assertThat(service.isTrustedExact("conversation-1", "server-a", "/srv/other", "systemctl restart api")).isFalse();
    }

    private ServerEntity server() {
        return new ServerEntity("server-a", "服务器 A", "host", 22, "ops", ServerAuthenticationType.PASSWORD,
                "ciphertext", null, "host ssh-ed25519 AAAATESTKEY", true);
    }

    private ServerCommandEntity dangerousCommand() {
        return new ServerCommandEntity("command-1", "server-a", "重启服务", "重启服务",
                "sudo -n systemctl restart nginx", "[]", ServerCommandRisk.DANGEROUS, true, 0);
    }

    private ServerAssistantProperties properties() {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setApprovalTtl(Duration.ofMinutes(10));
        return properties;
    }

    private ServerActionContinuationService continuationService() {
        ServerActionContinuationService service = mock(ServerActionContinuationService.class);
        when(service.prepare(eq("conversation-1"), eq("server-a"), argThat(message ->
                message.contains("系统可信事件")))).thenReturn("continuation-1");
        return service;
    }
}
