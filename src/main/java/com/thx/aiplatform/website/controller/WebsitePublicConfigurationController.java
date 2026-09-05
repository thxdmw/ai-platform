package com.thx.aiplatform.website.controller;

import com.thx.aiplatform.website.entity.WebsiteAssistantSettingsEntity;
import com.thx.aiplatform.website.vo.WebsitePublicConfiguration;
import com.thx.aiplatform.website.service.WebsiteSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开组件需要的助手名称、欢迎语和开关，不暴露后台提示词。 */
@RestController
@RequestMapping("/api/public/v1/website")
class WebsitePublicConfigurationController {

    private final WebsiteSettingsService settingsRepository;

    WebsitePublicConfigurationController(WebsiteSettingsService settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/configuration")
    WebsitePublicConfiguration configuration() {
        WebsiteAssistantSettingsEntity settings = settingsRepository.get();
        return new WebsitePublicConfiguration(settings.getAssistantName(), settings.getWelcomeMessage(), settings.isEnabled());
    }
}
