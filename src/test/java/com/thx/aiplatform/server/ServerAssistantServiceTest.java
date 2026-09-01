package com.thx.aiplatform.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thx.aiplatform.platform.AssistantChatGateway;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ServerAssistantServiceTest {

    @Test
    void 删除对话会同步清理模型记忆服务器绑定和待确认操作() {
        AssistantChatGateway chatGateway = mock(AssistantChatGateway.class);
        ServerOperationService operationService = mock(ServerOperationService.class);
        ServerCommandProposalService proposalService = mock(ServerCommandProposalService.class);
        ServerConversationBindingService bindingService = mock(ServerConversationBindingService.class);
        ServerAssistantService service = new ServerAssistantService(
                chatGateway, mock(ServerRegistry.class), mock(ServerConfigurationService.class),
                operationService, mock(SshCommandExecutor.class), mock(ObjectMapper.class), bindingService,
                proposalService);

        service.deleteConversation("conversation-1");

        verify(operationService).cancelForConversation("conversation-1");
        verify(proposalService).cancelForConversation("conversation-1");
        verify(bindingService).remove("conversation-1");
        verify(chatGateway).clear(ServerAssistantService.ASSISTANT_ID, "conversation-1");
    }
}
