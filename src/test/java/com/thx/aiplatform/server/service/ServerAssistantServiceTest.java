package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.model.ServerDefinition;
import com.thx.aiplatform.server.model.ServerContinuationRequest;
import com.thx.aiplatform.server.model.ServerAuthenticationType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thx.aiplatform.platform.AssistantChatGateway;
import com.thx.aiplatform.platform.AssistantChatCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证编排层：续跑用服务端可信消息恢复同一模型会话，删除对话时联动清理各服务。 */
class ServerAssistantServiceTest {

    @Test
    void 动作完成后使用服务端续跑消息恢复同一模型会话() {
        AssistantChatGateway chatGateway = mock(AssistantChatGateway.class);
        ServerRegistry registry = mock(ServerRegistry.class);
        ServerDefinition server = new ServerDefinition("server-a", "服务器 A", "host", 22, "ops",
                ServerAuthenticationType.PASSWORD, "ciphertext", null, "host ssh-ed25519 AAAATESTKEY", true);
        when(registry.requireEnabled("server-a")).thenReturn(server);
        when(chatGateway.stream(any(AssistantChatCommand.class), any(Object[].class))).thenReturn(Flux.empty());
        ServerActionContinuationService continuationService = mock(ServerActionContinuationService.class);
        when(continuationService.consume("continuation-1", "conversation-1", "server-a"))
                .thenReturn("系统可信事件：命令已经添加，请继续原任务");
        ServerConversationBindingService bindingService = mock(ServerConversationBindingService.class);
        ServerAssistantService service = new ServerAssistantService(
                chatGateway, registry, mock(ServerConfigurationService.class), mock(ServerOperationService.class),
                mock(SshCommandExecutor.class), mock(ObjectMapper.class), bindingService,
                mock(ServerCommandProposalService.class), continuationService,
                mock(ServerCommandTemplateService.class), mock(ServerModelProviderService.class));

        service.continueAfterAction(new ServerContinuationRequest(
                "conversation-1", "server-a", "continuation-1", null)).blockLast();

        ArgumentCaptor<AssistantChatCommand> command = ArgumentCaptor.forClass(AssistantChatCommand.class);
        verify(chatGateway).stream(command.capture(), any(Object[].class));
        assertThat(command.getValue().conversationId()).isEqualTo("conversation-1");
        assertThat(command.getValue().userMessage()).contains("命令已经添加");
        verify(bindingService).bind("conversation-1", "server-a");
    }

    @Test
    void 删除对话会同步清理模型记忆服务器绑定和待确认操作() {
        AssistantChatGateway chatGateway = mock(AssistantChatGateway.class);
        ServerOperationService operationService = mock(ServerOperationService.class);
        ServerCommandProposalService proposalService = mock(ServerCommandProposalService.class);
        ServerActionContinuationService continuationService = mock(ServerActionContinuationService.class);
        ServerConversationBindingService bindingService = mock(ServerConversationBindingService.class);
        ServerAssistantService service = new ServerAssistantService(
                chatGateway, mock(ServerRegistry.class), mock(ServerConfigurationService.class),
                operationService, mock(SshCommandExecutor.class), mock(ObjectMapper.class), bindingService,
                proposalService, continuationService,
                mock(ServerCommandTemplateService.class), mock(ServerModelProviderService.class));

        service.deleteConversation("conversation-1");

        verify(operationService).cancelForConversation("conversation-1");
        verify(proposalService).cancelForConversation("conversation-1");
        verify(continuationService).cancelForConversation("conversation-1");
        verify(bindingService).remove("conversation-1");
        verify(chatGateway).clear(ServerAssistantService.ASSISTANT_ID, "conversation-1");
    }
}
