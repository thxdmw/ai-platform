package com.thx.aiplatform.blog;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class BlogApiClient {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final BlogAssistantProperties properties;
    private final HttpClient httpClient;

    BlogApiClient(BlogAssistantProperties properties) {
        this.properties = properties;
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

    String postForm(String endpoint, Map<String, String> parameters) {
        HttpRequest request = requestBuilder(endpointUrl(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(parameters), StandardCharsets.UTF_8))
                .build();
        return execute(request);
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

    static Map<String, String> publicationParameters(BlogPublicationRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("title", request.title());
        parameters.put("contentMd", request.contentMd());
        if (request.categoryId() != null) parameters.put("categoryId", request.categoryId().toString());
        if (request.tagIds() != null) parameters.put("tagIds", request.tagIds());
        if (request.description() != null) parameters.put("description", request.description());
        if (request.keywords() != null) parameters.put("keywords", request.keywords());
        if (request.coverImage() != null) parameters.put("coverImage", request.coverImage());
        parameters.put("author", request.author() == null ? "AI Assistant" : request.author());
        return parameters;
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
