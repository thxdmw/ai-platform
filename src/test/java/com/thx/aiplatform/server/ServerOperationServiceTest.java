package com.thx.aiplatform.server;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServerOperationServiceTest {

    @Test
    void 危险命令必须经过确认且只能执行一次() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerDefinition server = server();
        ServerCommandDefinition command = dangerousCommand();
        when(executor.execute(same(server), eq(command.commandText())))
                .thenReturn(new SshExecutionResult("server-a", 0, "active", "", 120, false));
        ServerOperationService service = new ServerOperationService(
                executor, properties(), Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        PendingServerOperationView pending = service.prepare("conversation-1", server, command, "服务异常");
        verifyNoInteractions(executor);

        ServerOperationResult result = service.approve(pending.actionId());
        assertThat(result.success()).isTrue();
        verify(executor).execute(same(server), eq(command.commandText()));
        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    @Test
    void 普通命令不能伪装成待确认危险操作() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerOperationService service = new ServerOperationService(executor, properties(), Clock.systemUTC());
        ServerCommandDefinition command = new ServerCommandDefinition(
                "command-2", "server-a", "查看状态", "查看状态", "uptime", ServerCommandRisk.NORMAL, true, 0);

        assertThatThrownBy(() -> service.prepare("conversation-1", server(), command, "检查"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不需要");
        verifyNoInteractions(executor);
    }

    @Test
    void 连接异常时消费操作并提示先核对实际状态() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerDefinition server = server();
        ServerCommandDefinition command = dangerousCommand();
        when(executor.execute(same(server), eq(command.commandText())))
                .thenThrow(new IllegalStateException("连接中断"));
        ServerOperationService service = new ServerOperationService(
                executor, properties(), Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        PendingServerOperationView pending = service.prepare("conversation-1", server, command, "服务异常");
        ServerOperationResult result = service.approve(pending.actionId());

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("结果不确定").contains("连接中断");
        assertThat(result.execution()).isNull();
        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    private ServerDefinition server() {
        return new ServerDefinition("server-a", "服务器 A", "host", 22, "ops", ServerAuthenticationType.PASSWORD,
                "ciphertext", null, "host ssh-ed25519 AAAATESTKEY", true);
    }

    private ServerCommandDefinition dangerousCommand() {
        return new ServerCommandDefinition("command-1", "server-a", "重启服务", "重启服务",
                "sudo -n systemctl restart nginx", ServerCommandRisk.DANGEROUS, true, 0);
    }

    private ServerAssistantProperties properties() {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setApprovalTtl(Duration.ofMinutes(10));
        return properties;
    }
}
