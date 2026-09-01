package com.thx.aiplatform.blog.tool;
import com.thx.aiplatform.blog.service.BlogApiClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 博客只读查询工具集，绑定进模型工具列表。核心决策：网络/上游失败一律转成「暂时不可用」的文本返回
 * 而非抛异常——工具调用异常会直接中断整轮模型会话，文本返回让模型能继续回答并如实告知用户；
 * 所有响应截断到 16KB，防止大 JSON 撑爆模型上下文。
 */
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

    /**
     * ID 会直接拼进请求路径，先做白名单格式校验，防止异常字符构造出越界请求。
     */
    @Tool(description = "根据文章 ID 获取完整博客详情")
    public String getBlogDetail(@ToolParam(description = "文章 ID") String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) return "文章 ID 格式不正确";
        return invoke("文章详情", () -> bounded(apiClient.get("/articles/" + id)));
    }

    @Tool(description = "获取可用于撰写和发布文章的全部有效分类与标签")
    public String getBlogTaxonomy() {
        return invoke("分类与标签", () -> bounded(apiClient.get("/taxonomy")));
    }

    /**
     * 异常必须在这里吞掉并转文本：任何抛出都会让模型工具调用失败并中断整轮会话，
     * 这是查询工具可用性的底线——宁可让模型说「暂时不可用」，也不能让会话断掉。
     */
    private String invoke(String operation, Query query) {
        try {
            return query.execute();
        } catch (RuntimeException exception) {
            log.warn("博客只读工具调用失败，operation={}，reason={}", operation, exception.getMessage());
            return operation + "暂时不可用：" + exception.getMessage();
        }
    }

    /**
     * 截断而非丢弃：保留可用的前缀，并留下「已截断」标记，让模型知道数据不完整、不该据此下结论。
     */
    private String bounded(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        return raw.length() <= RAW_RESPONSE_LIMIT ? raw : raw.substring(0, RAW_RESPONSE_LIMIT) + "...[响应已截断]";
    }

    @FunctionalInterface
    private interface Query {
        String execute();
    }
}
