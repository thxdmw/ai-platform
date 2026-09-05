package com.thx.aiplatform.blog.service;
import com.thx.aiplatform.blog.dto.BlogPublicationRequest;
import com.thx.aiplatform.blog.config.BlogAssistantProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 博客系统 Agent API 的唯一 HTTP 出口：统一建连/请求超时与 API Key 头。
 * 查询接口返回原始 JSON 字符串——结果要原样转给模型，先解析再序列化会丢字段；
 * 非 2xx 与网络失败统一抛 BlogApiException，由调用方决定兜底策略。
 */
@Component
public class BlogApiClient {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final BlogAssistantProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    BlogApiClient(BlogAssistantProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    public String get(String endpoint) {
        return get(endpoint, Map.of());
    }

    public String get(String endpoint, Map<String, String> parameters) {
        String query = encodeForm(parameters);
        String url = endpointUrl(endpoint) + (query.isEmpty() ? "" : "?" + query);
        HttpRequest request = requestBuilder(url).GET().build();
        return execute(request);
    }

    /**
     * categoryId 缺省 1、author 缺省 "AI Assistant"：与博客系统约定好的发布默认值，
     * 模拟真实后台的人工发布行为。public：发布实现从 service.impl 子包调用，跨包必须公开。
     */
    public String publish(BlogPublicationRequest publication) {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("title", publication.title()),
                Map.entry("contentMd", publication.contentMd()),
                Map.entry("categoryId", publication.categoryId() == null ? "1" : publication.categoryId()),
                Map.entry("tagIds", splitTagIds(publication.tagIds())),
                Map.entry("description", valueOrEmpty(publication.description())),
                Map.entry("keywords", valueOrEmpty(publication.keywords())),
                Map.entry("coverImage", valueOrEmpty(publication.coverImage())),
                Map.entry("author", publication.author() == null ? "AI Assistant" : publication.author())
        );
        try {
            HttpRequest request = requestBuilder(endpointUrl("/articles"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return execute(request);
        } catch (JsonProcessingException exception) {
            throw new BlogApiException("无法生成博客发布请求", exception);
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json");
        if (!properties.getApiKey().isBlank()) {
            builder.header(API_KEY_HEADER, properties.getApiKey());
        }
        return builder;
    }

    private String endpointUrl(String endpoint) {
        return properties.getApiBaseUrl() + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
    }

    private String encodeForm(Map<String, String> parameters) {
        StringBuilder encoded = new StringBuilder();
        parameters.forEach((name, value) -> {
            if (value == null) {
                return;
            }
            if (!encoded.isEmpty()) {
                encoded.append('&');
            }
            encoded.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return encoded.toString();
    }

    /**
     * 非 2xx 一律抛异常而不是返回错误体：查询工具依赖异常路径把故障转成「不可用」文本，
     * 若把错误 JSON 当正常响应返回，会污染模型上下文。
     */
    private String execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BlogApiException("博客系统返回 HTTP " + response.statusCode());
            }
            return response.body() == null ? "" : response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BlogApiException("博客系统请求被中断", exception);
        } catch (IOException exception) {
            throw new BlogApiException("无法连接博客系统", exception);
        }
    }

    /**
     * 去重并限制最多 10 个标签：模型可能重复或过量传标签，这道约束防止单次发布撑爆上游表单。
     */
    private List<String> splitTagIds(String tagIds) {
        if (tagIds == null || tagIds.isBlank()) return List.of();
        return Arrays.stream(tagIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(10)
                .toList();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    static class BlogApiException extends RuntimeException {
        BlogApiException(String message) {
            super(message);
        }

        BlogApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
