package com.thx.aiplatform.blog.service;
import com.thx.aiplatform.blog.tool.BlogQueryTools;
import com.thx.aiplatform.blog.tool.BlogPublicationTool;
import com.thx.aiplatform.blog.dto.BlogChatRequest;

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

/** 会话编排：固定助手身份、工具装配、消息 trim 与会话候选作废。 */
class BlogAssistantServiceTest {

    @Test
    void 博客助手固定身份并装配查询与发布选项工具() {
        AssistantChatGateway gateway = mock(AssistantChatGateway.class);
        BlogQueryTools queryTools = mock(BlogQueryTools.class);
        BlogPublicationService publicationService = mock(BlogPublicationService.class);
        when(gateway.stream(any(AssistantChatCommand.class), any(Object[].class)))
                .thenReturn(Flux.just("回答"));
        BlogAssistantService service = new BlogAssistantService(gateway, queryTools, publicationService);

        assertThat(service.stream(new BlogChatRequest("session-1", " 查询最新文章 ")).blockLast())
                .isEqualTo("回答");

        ArgumentCaptor<AssistantChatCommand> command = ArgumentCaptor.forClass(AssistantChatCommand.class);
        ArgumentCaptor<Object[]> tools = ArgumentCaptor.forClass(Object[].class);
        verify(gateway).stream(command.capture(), tools.capture());
        assertThat(command.getValue().assistantId()).isEqualTo("blog-admin");
        assertThat(command.getValue().userMessage()).isEqualTo("查询最新文章");
        assertThat(tools.getValue()).hasSize(2);
        assertThat(tools.getValue()[0]).isSameAs(queryTools);
        assertThat(tools.getValue()[1]).isInstanceOf(BlogPublicationTool.class);
        verify(publicationService).cancelForConversation("session-1");
    }
}
