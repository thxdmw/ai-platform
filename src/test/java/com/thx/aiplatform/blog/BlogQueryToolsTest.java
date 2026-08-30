package com.thx.aiplatform.blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogQueryToolsTest {

    @Test
    void 详情接口直接透传精简响应() throws Exception {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        when(apiClient.get("/articles/7")).thenReturn("""
                {"data":{"id":7,"title":"测试","tags":[{"id":1},{"id":2}]}}
                """);
        BlogQueryTools tools = new BlogQueryTools(apiClient);

        String result = tools.getBlogDetail("7");

        assertThat(new ObjectMapper().readTree(result).path("data").path("tags")).hasSize(2);
    }

    @Test
    void 博客概览限制最新文章请求数量() {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        when(apiClient.get("/overview", Map.of("recentLimit", "10"))).thenReturn("{\"data\":{}}");
        BlogQueryTools tools = new BlogQueryTools(apiClient);

        String result = tools.getBlogOverview(99);

        assertThat(result).isEqualTo("{\"data\":{}}");
    }
}
