package com.thx.aiplatform.server.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.thx.aiplatform.server.entity.ServerModelEntity;
import com.thx.aiplatform.server.entity.ServerModelProviderEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 自定义模型提供方与模型清单的 MyBatis-Plus 数据访问层，实体同时作为服务层内部流转的领域对象。 */
@Repository
public class ServerModelProviderRepository {

    private final ServerModelProviderMapper providerMapper;
    private final ServerModelMapper modelMapper;

    ServerModelProviderRepository(ServerModelProviderMapper providerMapper, ServerModelMapper modelMapper) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
    }

    public List<ServerModelProviderEntity> findProviders() {
        return providerMapper.selectList(Wrappers.<ServerModelProviderEntity>query()
                .orderByAsc("created_at").orderByAsc("name"));
    }

    public Optional<ServerModelProviderEntity> findProvider(String id) {
        return Optional.ofNullable(providerMapper.selectById(id));
    }

    public void insertProvider(ServerModelProviderEntity value) {
        providerMapper.insert(value);
    }

    public void updateProvider(ServerModelProviderEntity value) {
        int count = providerMapper.update(value, Wrappers.<ServerModelProviderEntity>lambdaUpdate()
                .eq(ServerModelProviderEntity::getId, value.getId())
                .setSql("updated_at = CURRENT_TIMESTAMP"));
        if (count == 0) throw new IllegalArgumentException("模型提供方不存在");
    }

    public void deleteProvider(String id) {
        if (providerMapper.deleteById(id) == 0) throw new IllegalArgumentException("模型提供方不存在");
    }

    public List<ServerModelEntity> findModels(String providerId, boolean onlyEnabled) {
        return modelMapper.selectList(Wrappers.<ServerModelEntity>query()
                .eq("provider_id", providerId)
                .eq(onlyEnabled, "enabled", true)
                .orderByAsc("sort_order").orderByAsc("created_at").orderByAsc("name"));
    }

    public Optional<ServerModelEntity> findModel(String id) {
        return Optional.ofNullable(modelMapper.selectById(id));
    }

    /** 先删后插整体替换某提供方的模型清单，调用方（服务层）负责整体事务边界。 */
    public void replaceModels(String providerId, List<ServerModelEntity> models) {
        modelMapper.delete(Wrappers.<ServerModelEntity>lambdaQuery()
                .eq(ServerModelEntity::getProviderId, providerId));
        for (ServerModelEntity model : models) {
            modelMapper.insert(model);
        }
    }
}