package com.thx.aiplatform.website.controller;

import com.thx.aiplatform.website.model.WebsiteAssistantSettings;
import com.thx.aiplatform.website.model.WebsitePublicConfiguration;
import com.thx.aiplatform.website.repository.WebsiteSettingsRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开组件需要的助手名称、欢迎语和开关，不暴露后台提示词。 */
@RestController
@RequestMapping("/api/public/v1/website")
class WebsitePublicConfigurationController {

    private final WebsiteSettingsRepository settingsRepository;

    WebsitePublicConfigurationController(WebsiteSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/configuration")
    WebsitePublicConfiguration configuration() {
        WebsiteAssistantSettings settings = settingsRepository.get();
        return new WebsitePublicConfiguration(settings.assistantName(), settings.welcomeMessage(), settings.enabled());
    }
}
