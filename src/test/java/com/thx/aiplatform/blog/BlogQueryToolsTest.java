package com.thx.aiplatform.blog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlogQueryToolsTest {

    @Test
    void 详情压缩不会误删文章标签() throws Exception {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        when(apiClient.get("/getBlogDetailById", Map.of("id", "7"))).thenReturn("""
                {"data":{"id":7,"title":"测试","tags":[{"id":1},{"id":2}]}}
                """);
        BlogQueryTools tools = new BlogQueryTools(apiClient, new ObjectMapper());

        String result = tools.getBlogDetailById(7);

        assertThat(new ObjectMapper().readTree(result).path("data").path("tags")).hasSize(2);
    }

    @Test
    void 最新文章结果受请求数量限制() throws Exception {
        BlogApiClient apiClient = mock(BlogApiClient.class);
        when(apiClient.get("/getRecentBlogs", Map.of("pageSize", "2"))).thenReturn("""
                {"data":[{"id":1},{"id":2},{"id":3}]}
                """);
        BlogQueryTools tools = new BlogQueryTools(apiClient, new ObjectMapper());

        String result = tools.getRecentBlogs(2);

        assertThat(new ObjectMapper().readTree(result).path("data")).hasSize(2);
    }
}
