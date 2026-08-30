package com.thx.aiplatform.blog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BlogQueryTools {

    private static final Logger log = LoggerFactory.getLogger(BlogQueryTools.class);
    private static final int RAW_RESPONSE_LIMIT = 16_000;

    private final BlogApiClient apiClient;
    BlogQueryTools(BlogApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Tool(description = "获取博客文章、分类、标签数量和最新文章摘要")
    public String getBlogOverview(
            @ToolParam(description = "最新文章数量，范围 1 到 10", required = false) Integer recentLimit
    ) {
        int limit = recentLimit == null ? 5 : Math.max(1, Math.min(recentLimit, 10));
        return invoke("博客概览", () -> bounded(apiClient.get("/overview", Map.of("recentLimit", String.valueOf(limit)))));
    }

    @Tool(description = "根据关键词搜索博客文章，返回精简的文章 ID 和标题，最多 20 篇")
    public String searchBlogs(@ToolParam(description = "搜索关键词") String keyword,
                              @ToolParam(description = "返回数量，范围 1 到 20", required = false) Integer resultLimit) {
        int limit = resultLimit == null ? 10 : Math.max(1, Math.min(resultLimit, 20));
        return invoke("文章搜索", () -> bounded(apiClient.get("/articles/search", Map.of(
                "keyword", keyword,
                "limit", String.valueOf(limit)
        ))));
    }

    @Tool(description = "根据文章 ID 获取完整博客详情")
    public String getBlogDetail(@ToolParam(description = "文章 ID") String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) return "文章 ID 格式不正确";
        return invoke("文章详情", () -> bounded(apiClient.get("/articles/" + id)));
    }

    @Tool(description = "获取可用于撰写和发布文章的全部有效分类与标签")
    public String getBlogTaxonomy() {
        return invoke("分类与标签", () -> bounded(apiClient.get("/taxonomy")));
    }

    private String invoke(String operation, Query query) {
        try {
            return query.execute();
        } catch (RuntimeException exception) {
            log.warn("博客只读工具调用失败，operation={}，reason={}", operation, exception.getMessage());
            return operation + "暂时不可用：" + exception.getMessage();
        }
    }

    private String bounded(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        return raw.length() <= RAW_RESPONSE_LIMIT ? raw : raw.substring(0, RAW_RESPONSE_LIMIT) + "...[响应已截断]";
    }

    @FunctionalInterface
    private interface Query {
        String execute();
    }
}
