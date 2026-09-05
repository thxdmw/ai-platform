package com.thx.aiplatform.server.service;

import com.thx.aiplatform.platform.AssistantModelConnection;
import com.thx.aiplatform.server.dto.ServerModelProviderRequest;
import com.thx.aiplatform.server.entity.ServerModelProviderEntity;
import com.thx.aiplatform.server.vo.ServerModelProviderView;
import com.thx.aiplatform.server.vo.ServerModelView;

import java.util.List;
import java.util.Optional;

/**
 * 自定义模型提供方与模型清单的业务入口：CRUD、密钥加密入库与模型连接解析，同时承载
 * 提供方/模型的查询能力（供探测与测试使用）。
 */
public interface ServerModelProviderService {

    List<ServerModelProviderView> listProviders();

    List<ServerModelView> listEnabledModels();

    ServerModelProviderView create(ServerModelProviderRequest request);

    ServerModelProviderView update(String id, ServerModelProviderRequest request);

    void delete(String id);

    AssistantModelConnection resolve(String modelId, String requestedReasoningEffort);

    List<ServerModelProviderEntity> findProviders();

    Optional<ServerModelProviderEntity> findProvider(String id);
}