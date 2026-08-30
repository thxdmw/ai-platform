package com.thx.aiplatform.server;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServerOperationServiceTest {

    @Test
    void 白名单操作必须经过确认且只能执行一次() {
        ServerRegistry registry = mock(ServerRegistry.class);
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerDefinition server = server();
        when(registry.require("server-a")).thenReturn(server);
        when(executor.execute(same(server), contains("systemctl restart")))
                .thenReturn(new SshExecutionResult("server-a", 0, "active", "", 120, false));
        ServerOperationService service = new ServerOperationService(
                registry, executor, properties(), Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        PendingServerOperationView pending = service.prepare(
                "conversation-1", "server-a", "RESTART_SERVICE", "nginx", "服务异常");
        verifyNoInteractions(executor);

        ServerOperationResult result = service.approve(pending.actionId());
        assertThat(result.success()).isTrue();
        verify(executor).execute(same(server), contains("systemctl restart"));
        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    @Test
    void 非白名单目标不能生成操作选项() {
        ServerRegistry registry = mock(ServerRegistry.class);
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        when(registry.require("server-a")).thenReturn(server());
        ServerOperationService service = new ServerOperationService(registry, executor, properties(), Clock.systemUTC());

        assertThatThrownBy(() -> service.prepare(
                "conversation-1", "server-a", "RESTART_SERVICE", "mysql", "尝试重启"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");
        verifyNoInteractions(executor);
    }

    @Test
    void 连接异常时消费操作并提示先核对实际状态() {
        ServerRegistry registry = mock(ServerRegistry.class);
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerDefinition server = server();
        when(registry.require("server-a")).thenReturn(server);
        when(executor.execute(same(server), contains("systemctl restart")))
                .thenThrow(new IllegalStateException("连接中断"));
        ServerOperationService service = new ServerOperationService(
                registry, executor, properties(), Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        PendingServerOperationView pending = service.prepare(
                "conversation-1", "server-a", "RESTART_SERVICE", "nginx", "服务异常");

        ServerOperationResult result = service.approve(pending.actionId());
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("结果不确定").contains("连接中断");
        assertThat(result.execution()).isNull();
        assertThatThrownBy(() -> service.approve(pending.actionId())).hasMessageContaining("不存在或已处理");
    }

    private ServerDefinition server() {
        return new ServerDefinition("server-a", "服务器 A", "host", 22, "ops", "SERVER_A_PASSWORD", null,
                null, "known_hosts", List.of("nginx"), List.of("app"), true);
    }

    private ServerAssistantProperties properties() {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setApprovalTtl(Duration.ofMinutes(10));
        return properties;
    }
}
