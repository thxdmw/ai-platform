package com.thx.aiplatform.blog.service;
import com.thx.aiplatform.blog.model.PublicationResult;
import com.thx.aiplatform.blog.model.PendingPublicationView;
import com.thx.aiplatform.blog.model.BlogPublicationRequest;
import com.thx.aiplatform.blog.config.BlogAssistantProperties;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 发布候选生命周期：同一候选只能发布一次、过期后不得再调上游。 */
class BlogPublicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void 对话内确认后同一发布选项只能执行一次() {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        when(apiClient.publish(request().normalized())).thenReturn("{\"success\":true}");
        BlogPublicationService service = service(apiClient, Clock.fixed(NOW, ZoneOffset.UTC));
        PendingPublicationView pending = service.prepare("conversation-1", request());

        PublicationResult result = service.approve(pending.actionId());
        assertThat(result.success()).isTrue();
        verify(apiClient).publish(request().normalized());

        assertThatThrownBy(() -> service.approve(pending.actionId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或已处理");
        verifyNoMoreInteractions(apiClient);
    }

    @Test
    void 过期待审批任务不会调用博客发布接口() {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW, NOW.plus(Duration.ofMinutes(16)));
        BlogPublicationService service = service(apiClient, clock);
        PendingPublicationView pending = service.prepare("conversation-1", request());

        assertThatThrownBy(() -> service.approve(pending.actionId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
        verifyNoMoreInteractions(apiClient);
    }

    private BlogPublicationService service(BlogApiClient apiClient, Clock clock) {
        return new BlogPublicationService(apiClient, properties(), clock);
    }

    private BlogAssistantProperties properties() {
        BlogAssistantProperties properties = new BlogAssistantProperties();
        properties.setApprovalTtl(Duration.ofMinutes(15));
        return properties;
    }

    private BlogPublicationRequest request() {
        return new BlogPublicationRequest(
                " 测试文章 ",
                " # 正文 ",
                "1",
                "1,2",
                "摘要",
                "Java,AI",
                null,
                null
        );
    }
}
