package com.thx.aiplatform.website.service;
import com.thx.aiplatform.website.model.WebsiteChatRequest;

import com.thx.aiplatform.platform.AssistantChatCommand;
import com.thx.aiplatform.platform.AssistantChatGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务层测试：验证固定助手编号、公开知识被注入系统提示词、用户消息经 trim 后转发。
 */
class WebsiteAssistantServiceTest {

    @Test
    void 网站助手固定助手编号并注入公开知识() {
        AssistantChatGateway gateway = mock(AssistantChatGateway.class);
        WebsiteKnowledge knowledge = mock(WebsiteKnowledge.class);
        when(knowledge.content()).thenReturn("首页包含博客入口");
        when(gateway.stream(org.mockito.ArgumentMatchers.any())).thenReturn(Flux.just("请点击博客卡片"));
        WebsiteAssistantService service = new WebsiteAssistantService(gateway, knowledge);

        assertThat(service.stream(new WebsiteChatRequest("conversation-1", " 博客在哪里？ "))
                .collectList()
                .block()).containsExactly("请点击博客卡片");

        ArgumentCaptor<AssistantChatCommand> captor = ArgumentCaptor.forClass(AssistantChatCommand.class);
        verify(gateway).stream(captor.capture());
        AssistantChatCommand command = captor.getValue();
        assertThat(command.assistantId()).isEqualTo("website");
        assertThat(command.conversationId()).isEqualTo("conversation-1");
        assertThat(command.userMessage()).isEqualTo("博客在哪里？");
        assertThat(command.systemPrompt()).contains("首页包含博客入口");
        assertThat(command.systemPrompt()).contains("不执行代码、服务器、博客发布");
    }
}
