package com.thx.aiplatform.blog;

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

@Component
class BlogApiClient {

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

    String get(String endpoint) {
        return get(endpoint, Map.of());
    }

    String get(String endpoint, Map<String, String> parameters) {
        String query = encodeForm(parameters);
        String url = endpointUrl(endpoint) + (query.isEmpty() ? "" : "?" + query);
        HttpRequest request = requestBuilder(url).GET().build();
        return execute(request);
    }

    String publish(BlogPublicationRequest publication) {
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
