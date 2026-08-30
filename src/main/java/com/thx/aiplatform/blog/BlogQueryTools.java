package com.thx.aiplatform.blog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class BlogQueryTools {

    private static final Logger log = LoggerFactory.getLogger(BlogQueryTools.class);
    private static final int RAW_RESPONSE_LIMIT = 16_000;

    private final BlogApiClient apiClient;
    private final ObjectMapper objectMapper;

    BlogQueryTools(BlogApiClient apiClient, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "获取最新发布的博客文章，最多返回 10 篇")
    public String getRecentBlogs(
            @ToolParam(description = "返回数量，范围 1 到 10", required = false) Integer pageSize
    ) {
        int size = pageSize == null ? 10 : Math.max(1, Math.min(pageSize, 10));
        return invoke("最新文章", () -> compact(apiClient.get("/getRecentBlogs", Map.of("pageSize", String.valueOf(size))), size, false));
    }

    @Tool(description = "根据关键词搜索博客标题和内容，最多返回 20 篇")
    public String searchBlogs(@ToolParam(description = "搜索关键词") String keyword) {
        return invoke("文章搜索", () -> compact(apiClient.get("/searchBlogs", Map.of("keyword", keyword)), 20, false));
    }

    @Tool(description = "根据文章 ID 获取完整博客详情")
    public String getBlogDetailById(@ToolParam(description = "文章 ID") Integer id) {
        return invoke("文章详情", () -> compact(apiClient.get("/getBlogDetailById", Map.of("id", String.valueOf(id))), 1, true));
    }

    @Tool(description = "根据文章标题获取 Markdown 正文")
    public String getBlogContentByTitle(@ToolParam(description = "文章标题") String title) {
        return invoke("文章正文", () -> compact(apiClient.get("/getBlogContentByTitle", Map.of("title", title)), 1, true));
    }

    @Tool(description = "获取博客系统中的所有有效分类")
    public String getAllCategories() {
        return invoke("分类列表", () -> compact(apiClient.get("/getAllCategories"), 50, false));
    }

    @Tool(description = "获取博客系统中的所有标签")
    public String getAllTags() {
        return invoke("标签列表", () -> compact(apiClient.get("/getAllTags"), 50, false));
    }

    @Tool(description = "获取文章总数、分类数、标签数等博客统计信息")
    public String getSystemStats() {
        return invoke("博客统计", () -> compact(apiClient.get("/getSystemStats"), 10, false));
    }

    private String invoke(String operation, Query query) {
        try {
            return query.execute();
        } catch (RuntimeException exception) {
            log.warn("博客只读工具调用失败，operation={}，reason={}", operation, exception.getMessage());
            return operation + "暂时不可用：" + exception.getMessage();
        }
    }

    private String compact(String raw, int maxItems, boolean includeContent) {
        if (raw == null || raw.isBlank()) return "{}";
        try {
            JsonNode root = objectMapper.readTree(raw);
            trimNode(root, includeContent);
            capResultLists(root, maxItems, root instanceof ArrayNode);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            return raw.length() <= RAW_RESPONSE_LIMIT ? raw : raw.substring(0, RAW_RESPONSE_LIMIT) + "...[响应已截断]";
        }
    }

    private void trimNode(JsonNode node, boolean includeContent) {
        if (node instanceof ObjectNode object) {
            List<String> remove = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = object.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                if (!includeContent && ("content".equalsIgnoreCase(name) || "contentMd".equalsIgnoreCase(name))) {
                    remove.add(name);
                } else if (field.getValue().isTextual() && field.getValue().textValue().length() > (includeContent ? 16_000 : 1_000)) {
                    int limit = includeContent ? 16_000 : 1_000;
                    object.put(name, field.getValue().textValue().substring(0, limit) + "...[字段已截断]");
                } else {
                    trimNode(field.getValue(), includeContent);
                }
            }
            remove.forEach(object::remove);
        } else if (node instanceof ArrayNode array) {
            array.forEach(item -> trimNode(item, includeContent));
        }
    }

    private void capResultLists(JsonNode node, int maxItems, boolean capCurrentArray) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(field -> {
                JsonNode value = field.getValue();
                capResultLists(value, maxItems, value instanceof ArrayNode && isResultList(field.getKey()));
            });
        } else if (node instanceof ArrayNode array) {
            if (capCurrentArray) {
                while (array.size() > maxItems) array.remove(array.size() - 1);
            }
            array.forEach(item -> capResultLists(item, maxItems, false));
        }
    }

    private boolean isResultList(String name) {
        return "data".equalsIgnoreCase(name) || "records".equalsIgnoreCase(name)
                || "list".equalsIgnoreCase(name) || "items".equalsIgnoreCase(name)
                || "content".equalsIgnoreCase(name);
    }

    @FunctionalInterface
    private interface Query {
        String execute();
    }
}
