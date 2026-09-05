package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.vo.PendingServerOperationView;
import com.thx.aiplatform.server.model.ServerAuthenticationType;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.model.SshExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证临时命令的核心安全边界：只读自动执行，无法证明只读时只生成审批。 */
class ServerTemporaryCommandServiceTest {

    @Test
    void 只读命令在安全引用工作目录后自动执行() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerEntity server = server();
        when(executor.execute(same(server), eq("cd -- '/srv/app' && git status --short")))
                .thenReturn(new SshExecutionResult("server-a", 0, "clean", "", 12, false));
        ServerOperationService operationService = mock(ServerOperationService.class);
        ServerTemporaryCommandService service = new ServerTemporaryCommandService(
                new ServerCommandRiskClassifier(), operationService, executor);

        String result = service.executeOrPrepare(
                "conversation-1", server, "git status --short", "/srv/app", "检查仓库状态");

        assertThat(result).contains("clean");
        verify(executor).execute(same(server), eq("cd -- '/srv/app' && git status --short"));
        verify(operationService, never()).prepareTemporary(anyString(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void 写命令只生成审批且不接触Ssh执行器() {
        SshCommandExecutor executor = mock(SshCommandExecutor.class);
        ServerOperationService operationService = mock(ServerOperationService.class);
        when(operationService.prepareTemporary(anyString(), any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new PendingServerOperationView("action-1", "server-a", "服务器 A", "temporary",
                        "临时 Shell 命令", "touch ready", "/srv/app", true, "创建标记", Instant.now(),
                        "PENDING_APPROVAL", "EXECUTE_TEMPORARY_COMMAND"));
        ServerTemporaryCommandService service = new ServerTemporaryCommandService(
                new ServerCommandRiskClassifier(), operationService, executor);

        String result = service.executeOrPrepare(
                "conversation-1", server(), "touch ready", "/srv/app", "创建标记");

        assertThat(result).contains("任务已暂停").contains("需要用户选择");
        verifyNoInteractions(executor);
    }

    @Test
    void 工作目录必须是绝对路径且单独校验() {
        ServerTemporaryCommandService service = new ServerTemporaryCommandService(
                new ServerCommandRiskClassifier(), mock(ServerOperationService.class), mock(SshCommandExecutor.class));

        assertThatThrownBy(() -> service.executeOrPrepare(
                "conversation-1", server(), "pwd", "../app", "检查目录"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("绝对路径");
    }

    private ServerEntity server() {
        return new ServerEntity("server-a", "服务器 A", "host", 22, "ops", ServerAuthenticationType.PASSWORD,
                "ciphertext", null, "host ssh-ed25519 AAAATESTKEY", true);
    }
}
