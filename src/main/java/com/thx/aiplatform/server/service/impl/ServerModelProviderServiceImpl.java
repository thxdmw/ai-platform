package com.thx.aiplatform.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.thx.aiplatform.platform.AssistantModelConnection;
import com.thx.aiplatform.server.dto.ServerModelOptionRequest;
import com.thx.aiplatform.server.dto.ServerModelProviderRequest;
import com.thx.aiplatform.server.entity.ServerModelEntity;
import com.thx.aiplatform.server.entity.ServerModelProviderEntity;
import com.thx.aiplatform.server.enums.ServerModelApiProtocol;
import com.thx.aiplatform.server.repository.ServerModelMapper;
import com.thx.aiplatform.server.repository.ServerModelProviderMapper;
import com.thx.aiplatform.server.security.ServerCredentialCipher;
import com.thx.aiplatform.server.service.ServerModelProviderService;
import com.thx.aiplatform.server.vo.ServerModelProviderView;
import com.thx.aiplatform.server.vo.ServerModelView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 模型提供方与模型清单的实现：提供方与模型全部经 MyBatis-Plus 映射器落库；密钥只以
 * 密文形态进出（加密在 {@link ServerCredentialCipher}），模型清单整体先删后插替换。
 */
@Service
public class ServerModelProviderServiceImpl implements ServerModelProviderService {

    private final ServerModelProviderMapper providerMapper;
    private final ServerModelMapper modelMapper;
    private final ServerCredentialCipher cipher;

    ServerModelProviderServiceImpl(ServerModelProviderMapper providerMapper, ServerModelMapper modelMapper,
                                   ServerCredentialCipher cipher) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.cipher = cipher;
    }

    @Override
    public List<ServerModelProviderView> listProviders() {
        return findProviders().stream().map(this::toView).toList();
    }

    @Override
    public List<ServerModelView> listEnabledModels() {
        return findProviders().stream().filter(ServerModelProviderEntity::isEnabled)
                .flatMap(provider -> findModels(provider.getId(), true).stream().map(model -> toView(provider, model)))
                .toList();
    }

    @Override
    @Transactional
    public ServerModelProviderView create(ServerModelProviderRequest request) {
        if (request.apiKey() == null || request.apiKey().isBlank()) throw new IllegalArgumentException("新增提供方时 API 密钥不能为空");
        String id = UUID.randomUUID().toString();
        ServerModelProviderEntity value = definition(id, request, cipher.encrypt(request.apiKey().trim()));
        providerMapper.insert(value);
        replaceModels(id, modelDefinitions(id, request.models(), java.util.Map.of()));
        return toView(value);
    }

    @Override
    @Transactional
    public ServerModelProviderView update(String id, ServerModelProviderRequest request) {
        ServerModelProviderEntity old = findProvider(id)
                .orElseThrow(() -> new IllegalArgumentException("模型提供方不存在"));
        java.util.Map<String, String> existingIds = findModels(id, false).stream()
                .collect(java.util.stream.Collectors.toMap(ServerModelEntity::getModelCode, ServerModelEntity::getId));
        String encryptedKey = request.apiKey() == null || request.apiKey().isBlank()
                ? old.getApiKeyCiphertext() : cipher.encrypt(request.apiKey().trim());
        ServerModelProviderEntity value = definition(id, request, encryptedKey);
        updateProvider(value);
        replaceModels(id, modelDefinitions(id, request.models(), existingIds));
        return toView(value);
    }

    @Override
    @Transactional
    public void delete(String id) {
        if (providerMapper.deleteById(id) == 0) throw new IllegalArgumentException("模型提供方不存在");
    }

    @Override
    public AssistantModelConnection resolve(String modelId, String requestedReasoningEffort) {
        if (modelId == null || modelId.isBlank()) return null;
        ServerModelEntity model = findModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("所选模型不存在"));
        ServerModelProviderEntity provider = findProvider(model.getProviderId())
                .orElseThrow(() -> new IllegalArgumentException("模型提供方不存在"));
        if (!provider.isEnabled() || !model.isEnabled()) throw new IllegalArgumentException("所选模型已停用");
        byte[] plaintext = cipher.decrypt(provider.getApiKeyCiphertext());
        try {
            String reasoningEffort = validateReasoningEffort(requestedReasoningEffort);
            if (reasoningEffort == null) reasoningEffort = emptyToNull(model.getReasoningEffort());
            return new AssistantModelConnection(provider.getBaseUrl(), provider.getChatCompletionsPath(), provider.getApiProtocol(),
                    new String(plaintext, StandardCharsets.UTF_8), model.getModelCode(), reasoningEffort);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override
    public List<ServerModelProviderEntity> findProviders() {
        return providerMapper.selectList(Wrappers.<ServerModelProviderEntity>query()
                .orderByAsc("created_at").orderByAsc("name"));
    }

    @Override
    public Optional<ServerModelProviderEntity> findProvider(String id) {
        return Optional.ofNullable(providerMapper.selectById(id));
    }

    private List<ServerModelEntity> findModels(String providerId, boolean onlyEnabled) {
        return modelMapper.selectList(Wrappers.<ServerModelEntity>query()
                .eq("provider_id", providerId)
                .eq(onlyEnabled, "enabled", true)
                .orderByAsc("sort_order").orderByAsc("created_at").orderByAsc("name"));
    }

    private Optional<ServerModelEntity> findModel(String id) {
        return Optional.ofNullable(modelMapper.selectById(id));
    }

    /** 先删后插整体替换某提供方的模型清单，调用方（create/update）负责整体事务边界。 */
    private void replaceModels(String providerId, List<ServerModelEntity> models) {
        modelMapper.delete(Wrappers.<ServerModelEntity>lambdaQuery()
                .eq(ServerModelEntity::getProviderId, providerId));
        for (ServerModelEntity model : models) {
            modelMapper.insert(model);
        }
    }

    private void updateProvider(ServerModelProviderEntity value) {
        int count = providerMapper.update(value, Wrappers.<ServerModelProviderEntity>lambdaUpdate()
                .eq(ServerModelProviderEntity::getId, value.getId())
                .setSql("updated_at = CURRENT_TIMESTAMP"));
        if (count == 0) throw new IllegalArgumentException("模型提供方不存在");
    }

    private ServerModelProviderEntity definition(String id, ServerModelProviderRequest request, String key) {
        String providerKey = request.providerKey() == null ? "" : request.providerKey().trim();
        if (!providerKey.matches("[a-z][a-z0-9-]{1,63}")) {
            throw new IllegalArgumentException("Provider ID 必须以小写字母开头，只能包含小写字母、数字和连字符");
        }
        if (findProviders().stream().anyMatch(provider ->
                provider.getProviderKey().equals(providerKey) && !provider.getId().equals(id))) {
            throw new IllegalArgumentException("Provider ID 已存在：" + providerKey);
        }
        String baseUrl = request.baseUrl().trim().replaceAll("/+$", "");
        if (!baseUrl.matches("https?://[^\\s]+")) throw new IllegalArgumentException("基础地址必须是 HTTP 或 HTTPS 地址");
        ServerModelApiProtocol protocol = ServerModelApiProtocol.fromCode(request.apiProtocol());
        String path = endpointPath(baseUrl, protocol.chatPath());
        return new ServerModelProviderEntity(id, providerKey, request.name().trim(), baseUrl,
                path, protocol.code(), key, request.enabled());
    }

    private List<ServerModelEntity> modelDefinitions(String providerId, List<ServerModelOptionRequest> requests,
                                                     java.util.Map<String, String> existingIds) {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("至少配置一个模型");
        Set<String> codes = new HashSet<>();
        List<ServerModelEntity> values = new ArrayList<>();
        for (ServerModelOptionRequest request : requests) {
            String code = request.modelCode().trim();
            if (!codes.add(code)) throw new IllegalArgumentException("同一提供方不能重复配置模型编号：" + code);
            values.add(new ServerModelEntity(existingIds.getOrDefault(code, UUID.randomUUID().toString()),
                    providerId, request.name().trim(), code,
                    emptyToNull(request.reasoningEffort()), request.enabled(), request.sortOrder()));
        }
        return values;
    }

    private ServerModelProviderView toView(ServerModelProviderEntity provider) {
        return new ServerModelProviderView(provider.getId(), provider.getProviderKey(), provider.getName(), provider.getBaseUrl(),
                provider.getApiProtocol(), provider.getApiKeyCiphertext() != null && !provider.getApiKeyCiphertext().isBlank(), provider.isEnabled(),
                findModels(provider.getId(), false).stream().map(model -> toView(provider, model)).toList());
    }

    private ServerModelView toView(ServerModelProviderEntity provider, ServerModelEntity model) {
        return new ServerModelView(model.getId(), provider.getId(), provider.getName(), provider.getApiProtocol(), model.getName(), model.getModelCode(),
                model.getReasoningEffort(), model.isEnabled(), model.getSortOrder());
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