package com.thx.aiplatform.website.repository;

import com.thx.aiplatform.website.entity.WebsiteAssistantSettingsEntity;
import com.thx.aiplatform.website.dto.WebsiteAssistantSettingsRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** 单例网站助手设置的持久化边界：配置表只有一行（id=1），读改写都围绕这一行。 */
@Repository
public class WebsiteSettingsRepository {

    private final WebsiteAssistantSettingsMapper mapper;

    public WebsiteSettingsRepository(WebsiteAssistantSettingsMapper mapper) {
        this.mapper = mapper;
    }

    public WebsiteAssistantSettingsEntity get() {
        WebsiteAssistantSettingsEntity entity = mapper.selectById(1);
        if (entity == null) {
            throw new IllegalStateException("网站助手设置不存在，请检查 Flyway 初始化数据");
        }
        return entity;
    }

    public WebsiteAssistantSettingsEntity update(WebsiteAssistantSettingsRequest request) {
        WebsiteAssistantSettingsEntity entity = new WebsiteAssistantSettingsEntity(
                request.assistantName().trim(),
                request.welcomeMessage().trim(),
                request.promptAddition() == null ? "" : request.promptAddition().trim(),
                request.enabled(),
                LocalDateTime.now());
        entity.setId(1);
        mapper.updateById(entity);
        return get();
    }
}