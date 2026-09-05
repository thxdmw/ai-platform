package com.thx.aiplatform.website.service;

import com.thx.aiplatform.website.dto.WebsiteAssistantSettingsRequest;
import com.thx.aiplatform.website.entity.WebsiteAssistantSettingsEntity;

/** 网站助手设置的读写服务：配置表只有一行（id=1），读改写都围绕这一行。 */
public interface WebsiteSettingsService {

    WebsiteAssistantSettingsEntity get();

    WebsiteAssistantSettingsEntity update(WebsiteAssistantSettingsRequest request);
}