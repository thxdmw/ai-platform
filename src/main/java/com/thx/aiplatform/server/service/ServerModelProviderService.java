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

    public AssistantModelConnection resolve(String modelId, String requestedReasoningEffort) {
        if (modelId == null || modelId.isBlank()) return null;
        ServerModelDefinition model = repository.findModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("所选模型不存在"));
        ServerModelProviderDefinition provider = repository.findProvider(model.providerId())
                .orElseThrow(() -> new IllegalArgumentException("模型提供方不存在"));
        if (!provider.enabled() || !model.enabled()) throw new IllegalArgumentException("所选模型已停用");
        byte[] plaintext = cipher.decrypt(provider.apiKeyCiphertext());
        try {
            String reasoningEffort = validateReasoningEffort(requestedReasoningEffort);
            if (reasoningEffort == null) reasoningEffort = emptyToNull(model.reasoningEffort());
            return new AssistantModelConnection(provider.baseUrl(), provider.chatCompletionsPath(), provider.apiProtocol(),
                    new String(plaintext, StandardCharsets.UTF_8), model.modelCode(), reasoningEffort);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private ServerModelProviderDefinition definition(String id, ServerModelProviderRequest request, String key) {
        String providerKey = request.providerKey() == null ? "" : request.providerKey().trim();
        if (!providerKey.matches("[a-z][a-z0-9-]{1,63}")) {
            throw new IllegalArgumentException("Provider ID 必须以小写字母开头，只能包含小写字母、数字和连字符");
        }
        if (repository.findProviders().stream().anyMatch(provider ->
                provider.providerKey().equals(providerKey) && !provider.id().equals(id))) {
            throw new IllegalArgumentException("Provider ID 已存在：" + providerKey);
        }
        String baseUrl = request.baseUrl().trim().replaceAll("/+$", "");
        if (!baseUrl.matches("https?://[^\\s]+")) throw new IllegalArgumentException("基础地址必须是 HTTP 或 HTTPS 地址");
        ServerModelApiProtocol protocol = ServerModelApiProtocol.fromCode(request.apiProtocol());
        String path = endpointPath(baseUrl, protocol.chatPath());
        return new ServerModelProviderDefinition(id, providerKey, request.name().trim(), baseUrl,
                path, protocol.code(), key, request.enabled());
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
        return new ServerModelProviderView(provider.id(), provider.providerKey(), provider.name(), provider.baseUrl(),
                provider.apiProtocol(), provider.apiKeyCiphertext() != null && !provider.apiKeyCiphertext().isBlank(), provider.enabled(),
                repository.findModels(provider.id(), false).stream().map(model -> toView(provider, model)).toList());
    }

    private ServerModelView toView(ServerModelProviderDefinition provider, ServerModelDefinition model) {
        return new ServerModelView(model.id(), provider.id(), provider.name(), provider.apiProtocol(), model.name(), model.modelCode(),
                model.reasoningEffort(), model.enabled(), model.sortOrder());
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String validateReasoningEffort(String value) {
        String normalized = emptyToNull(value);
        if (normalized == null || normalized.equals("auto")) return null;
        if (!Set.of("none", "minimal", "low", "medium", "high", "xhigh", "max").contains(normalized)) {
            throw new IllegalArgumentException("不支持的推理等级：" + normalized);
        }
        return normalized;
    }

    /** API 地址已经以 /v1 结尾时去掉协议路径重复的 /v1。 */
    private String endpointPath(String baseUrl, String path) {
        return baseUrl.endsWith("/v1") && path.startsWith("/v1/") ? path.substring(3) : path;
    }
}
