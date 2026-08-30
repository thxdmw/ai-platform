package com.thx.aiplatform.blog;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BlogPublicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void 发布必须显式确认且同一任务只能执行一次() {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        when(apiClient.postForm(eq("/publishBlog"), anyMap())).thenReturn("{\"success\":true}");
        BlogPublicationService service = service(apiClient, Clock.fixed(NOW, ZoneOffset.UTC));
        PendingPublicationView pending = service.prepare(request());

        assertThatThrownBy(() -> service.approve(pending.actionId(), "确认"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发布");

        PublicationResult result = service.approve(pending.actionId(), "发布");
        assertThat(result.success()).isTrue();
        verify(apiClient).postForm("/publishBlog", BlogApiClient.publicationParameters(request().normalized()));

        assertThatThrownBy(() -> service.approve(pending.actionId(), "发布"))
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
        PendingPublicationView pending = service.prepare(request());

        assertThatThrownBy(() -> service.approve(pending.actionId(), "发布"))
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
                1,
                "1,2",
                "摘要",
                "Java,AI",
                null,
                null
        );
    }
}
