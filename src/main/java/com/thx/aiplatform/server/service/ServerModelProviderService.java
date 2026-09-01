package com.thx.aiplatform.server.service;

import com.thx.aiplatform.platform.AssistantModelConnection;
import com.thx.aiplatform.server.model.*;
import com.thx.aiplatform.server.repository.ServerModelProviderRepository;
import com.thx.aiplatform.server.security.ServerCredentialCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ServerModelProviderService {

    private final ServerModelProviderRepository repository;
    private final ServerCredentialCipher cipher;

    ServerModelProviderService(ServerModelProviderRepository repository, ServerCredentialCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public List<ServerModelProviderView> listProviders() {
        return repository.findProviders().stream().map(this::toView).toList();
    }

    public List<ServerModelView> listEnabledModels() {
        return repository.findProviders().stream().filter(ServerModelProviderDefinition::enabled)
                .flatMap(provider -> repository.findModels(provider.id(), true).stream().map(model -> toView(provider, model)))
                .toList();
    }

    @Transactional
    public ServerModelProviderView create(ServerModelProviderRequest request) {
        if (request.apiKey() == null || request.apiKey().isBlank()) throw new IllegalArgumentException("新增提供方时 API 密钥不能为空");
        String id = UUID.randomUUID().toString();
        ServerModelProviderDefinition value = definition(id, request, cipher.encrypt(request.apiKey().trim()));
        repository.insertProvider(value);
        repository.replaceModels(id, modelDefinitions(id, request.models(), Map.of()));
        return toView(value);
    }

    @Transactional
    public ServerModelProviderView update(String id, ServerModelProviderRequest request) {
        ServerModelProviderDefinition old = repository.findProvider(id)
                .orElseThrow(() -> new IllegalArgumentException("模型提供方不存在"));
        Map<String, String> existingIds = repository.findModels(id, false).stream()
                .collect(java.util.stream.Collectors.toMap(ServerModelDefinition::modelCode, ServerModelDefinition::id));
        String encryptedKey = request.apiKey() == null || request.apiKey().isBlank()
                ? old.apiKeyCiphertext() : cipher.encrypt(request.apiKey().trim());
        ServerModelProviderDefinition value = definition(id, request, encryptedKey);
        repository.updateProvider(value);
        repository.replaceModels(id, modelDefinitions(id, request.models(), existingIds));
        return toView(value);
    }

    @Transactional
    public void delete(String id) { repository.deleteProvider(id); }

    public AssistantModelConnection resolve(String modelId) {
        if (modelId == null || modelId.isBlank()) return null;
        ServerModelDefinition model = repository.findModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("所选模型不存在"));
        ServerModelProviderDefinition provider = repository.findProvider(model.providerId())
                .orElseThrow(() -> new IllegalArgumentException("模型提供方不存在"));
        if (!provider.enabled() || !model.enabled()) throw new IllegalArgumentException("所选模型已停用");
        byte[] plaintext = cipher.decrypt(provider.apiKeyCiphertext());
        try {
            return new AssistantModelConnection(provider.baseUrl(), provider.chatCompletionsPath(),
                    new String(plaintext, StandardCharsets.UTF_8), model.modelCode(), emptyToNull(model.reasoningEffort()));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private ServerModelProviderDefinition definition(String id, ServerModelProviderRequest request, String key) {
        String baseUrl = request.baseUrl().trim().replaceAll("/+$", "");
        if (!baseUrl.matches("https?://[^\\s]+")) throw new IllegalArgumentException("基础地址必须是 HTTP 或 HTTPS 地址");
        String path = request.chatCompletionsPath().trim();
        if (!path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("对话接口路径必须以 / 开头且不能包含查询参数");
        }
        return new ServerModelProviderDefinition(id, request.name().trim(), baseUrl, path, key, request.enabled());
    }

    private List<ServerModelDefinition> modelDefinitions(String providerId, List<ServerModelOptionRequest> requests,
                                                         Map<String, String> existingIds) {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("至少配置一个模型");
        Set<String> codes = new HashSet<>();
        List<ServerModelDefinition> values = new ArrayList<>();
        for (ServerModelOptionRequest request : requests) {
            String code = request.modelCode().trim();
            if (!codes.add(code)) throw new IllegalArgumentException("同一提供方不能重复配置模型编号：" + code);
            values.add(new ServerModelDefinition(existingIds.getOrDefault(code, UUID.randomUUID().toString()),
                    providerId, request.name().trim(), code,
                    emptyToNull(request.reasoningEffort()), request.enabled(), request.sortOrder()));
        }
        return values;
    }

    private ServerModelProviderView toView(ServerModelProviderDefinition provider) {
        return new ServerModelProviderView(provider.id(), provider.name(), provider.baseUrl(), provider.chatCompletionsPath(),
                provider.apiKeyCiphertext() != null && !provider.apiKeyCiphertext().isBlank(), provider.enabled(),
                repository.findModels(provider.id(), false).stream().map(model -> toView(provider, model)).toList());
    }

    private ServerModelView toView(ServerModelProviderDefinition provider, ServerModelDefinition model) {
        return new ServerModelView(model.id(), provider.id(), provider.name(), model.name(), model.modelCode(),
                model.reasoningEffort(), model.enabled(), model.sortOrder());
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
