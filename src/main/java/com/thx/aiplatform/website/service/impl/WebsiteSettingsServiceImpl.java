package com.thx.aiplatform.website.service.impl;

import com.thx.aiplatform.website.dto.WebsiteAssistantSettingsRequest;
import com.thx.aiplatform.website.entity.WebsiteAssistantSettingsEntity;
import com.thx.aiplatform.website.repository.WebsiteAssistantSettingsMapper;
import com.thx.aiplatform.website.service.WebsiteSettingsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 网站助手设置的行级持久化实现：更新后回读最新行返回。 */
@Service
public class WebsiteSettingsServiceImpl implements WebsiteSettingsService {

    private final WebsiteAssistantSettingsMapper mapper;

    public WebsiteSettingsServiceImpl(WebsiteAssistantSettingsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public WebsiteAssistantSettingsEntity get() {
        WebsiteAssistantSettingsEntity entity = mapper.selectById(1);
        if (entity == null) {
            throw new IllegalStateException("网站助手设置不存在，请检查 Flyway 初始化数据");
        }
        return entity;
    }

    @Override
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