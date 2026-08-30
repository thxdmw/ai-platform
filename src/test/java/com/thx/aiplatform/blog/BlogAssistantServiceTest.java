package com.thx.aiplatform.blog;

import com.thx.aiplatform.platform.AssistantChatCommand;
import com.thx.aiplatform.platform.AssistantChatGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlogAssistantServiceTest {

    @Test
    void 博客助手固定身份并且只装配查询工具() {
        AssistantChatGateway gateway = mock(AssistantChatGateway.class);
        BlogQueryTools queryTools = mock(BlogQueryTools.class);
        when(gateway.stream(any(AssistantChatCommand.class), any(Object[].class)))
                .thenReturn(Flux.just("回答"));
        BlogAssistantService service = new BlogAssistantService(gateway, queryTools);

        assertThat(service.stream(new BlogChatRequest("session-1", " 查询最新文章 ")).blockLast())
                .isEqualTo("回答");

        ArgumentCaptor<AssistantChatCommand> command = ArgumentCaptor.forClass(AssistantChatCommand.class);
        ArgumentCaptor<Object[]> tools = ArgumentCaptor.forClass(Object[].class);
        verify(gateway).stream(command.capture(), tools.capture());
        assertThat(command.getValue().assistantId()).isEqualTo("blog-admin");
        assertThat(command.getValue().userMessage()).isEqualTo("查询最新文章");
        assertThat(tools.getValue()).containsExactly(queryTools);
    }
}
