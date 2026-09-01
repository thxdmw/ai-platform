package com.thx.aiplatform.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.thx.aiplatform.server.model.*;
import com.thx.aiplatform.server.repository.ServerModelProviderRepository;
import com.thx.aiplatform.server.security.ServerCredentialCipher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 提供方连接探测只读取模型目录，不发送聊天内容，既能验证地址与密钥，也不会产生模型费用。
 */
@Service
public class ServerModelProviderProbeService {

    private final ServerModelProviderRepository repository;
    private final ServerCredentialCipher cipher;
    private final RestClient restClient;

    ServerModelProviderProbeService(ServerModelProviderRepository repository, ServerCredentialCipher cipher,
                                    RestClient.Builder restClientBuilder) {
        this.repository = repository;
        this.cipher = cipher;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public ServerModelProviderProbeResult probe(ServerModelProviderProbeRequest request) {
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        ServerModelApiProtocol protocol = ServerModelApiProtocol.fromCode(request.apiProtocol());
        byte[] secret = resolveSecret(request);
        try {
            JsonNode response = restClient.get().uri(modelsEndpoint(baseUrl))
                    .headers(headers -> applyAuthentication(headers, protocol,
                            new String(secret, StandardCharsets.UTF_8)))
                    .retrieve().body(JsonNode.class);
            List<ServerModelCatalogEntry> models = readModels(response);
            return new ServerModelProviderProbeResult(true,
                    models.isEmpty() ? "连接成功，但提供方没有返回模型目录" : "连接成功，共发现 " + models.size() + " 个模型",
                    models);
        } catch (RestClientResponseException exception) {
            throw new IllegalArgumentException("提供方返回 HTTP " + exception.getStatusCode().value()
                    + "，请检查 API 地址、协议和密钥");
        } catch (Exception exception) {
            throw new IllegalArgumentException("连接提供方失败：" + safeMessage(exception));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private byte[] resolveSecret(ServerModelProviderProbeRequest request) {
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            return request.apiKey().trim().getBytes(StandardCharsets.UTF_8);
        }
        if (request.providerId() == null || request.providerId().isBlank()) {
            throw new IllegalArgumentException("测试连接时 API 密钥不能为空");
        }
        ServerModelProviderDefinition provider = repository.findProvider(request.providerId())
                .orElseThrow(() -> new IllegalArgumentException("模型提供方不存在"));
        return cipher.decrypt(provider.apiKeyCiphertext());
    }

    private void applyAuthentication(HttpHeaders headers, ServerModelApiProtocol protocol, String apiKey) {
        headers.set(HttpHeaders.ACCEPT, "application/json");
        if (protocol == ServerModelApiProtocol.ANTHROPIC_MESSAGES) {
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
        } else {
            headers.setBearerAuth(apiKey);
        }
    }

    private List<ServerModelCatalogEntry> readModels(JsonNode response) {
        if (response == null || !response.path("data").isArray()) return List.of();
        List<ServerModelCatalogEntry> result = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            String id = item.path("id").asText("").trim();
            if (id.isEmpty()) continue;
            String name = item.path("display_name").asText(item.path("name").asText(id)).trim();
            result.add(new ServerModelCatalogEntry(id, name.isEmpty() ? id : name));
        }
        result.sort(Comparator.comparing(ServerModelCatalogEntry::id));
        return List.copyOf(result);
    }

    private String modelsEndpoint(String baseUrl) {
        return baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl + "/v1/models";
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (!baseUrl.matches("https?://[^\\s]+")) throw new IllegalArgumentException("API 地址必须是 HTTP 或 HTTPS 地址");
        return baseUrl;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
