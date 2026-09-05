package com.thx.aiplatform.website.service;
import com.thx.aiplatform.website.dto.WebsiteChatRequest;

import com.thx.aiplatform.platform.AssistantChatCommand;
import com.thx.aiplatform.platform.AssistantChatGateway;
import com.thx.aiplatform.website.entity.WebsiteAssistantSettingsEntity;
import com.thx.aiplatform.website.service.impl.WebsiteAssistantServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

/**
 * 服务层测试：验证固定助手编号、公开知识被注入系统提示词、用户消息经 trim 后转发。
 */
class WebsiteAssistantServiceTest {

    @Test
    void 清理会话时固定使用网站助手编号() {
        AssistantChatGateway gateway = mock(AssistantChatGateway.class);
        WebsiteAssistantService service = new WebsiteAssistantServiceImpl(
                gateway,
                mock(WebsiteKnowledge.class),
                mock(WebsiteSettingsService.class)
        );

        service.clear("conversation-1");

        verify(gateway).clear("website", "conversation-1");
    }

    @Test
    void 网站助手固定助手编号并注入公开知识() {
        AssistantChatGateway gateway = mock(AssistantChatGateway.class);
        WebsiteKnowledge knowledge = mock(WebsiteKnowledge.class);
        WebsiteSettingsService settingsRepository = mock(WebsiteSettingsService.class);
        when(knowledge.contentFor("博客在哪里？")).thenReturn("首页包含博客入口");
        when(settingsRepository.get()).thenReturn(new WebsiteAssistantSettingsEntity(
                "网站助手", "你好", "优先回答首页入口", true, LocalDateTime.now()));
        when(gateway.stream(org.mockito.ArgumentMatchers.any())).thenReturn(Flux.just("请点击博客卡片"));
        WebsiteAssistantService service = new WebsiteAssistantServiceImpl(gateway, knowledge, settingsRepository);

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
        assertThat(command.systemPrompt()).contains("优先回答首页入口");
        assertThat(command.systemPrompt()).contains("不执行代码、服务器、博客发布");
    }
}
