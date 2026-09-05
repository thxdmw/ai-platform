package com.thx.aiplatform.website.controller;

import com.thx.aiplatform.website.entity.WebsiteAssistantSettingsEntity;
import com.thx.aiplatform.website.dto.WebsiteAssistantSettingsRequest;
import com.thx.aiplatform.website.entity.WebsiteKnowledgeEntryEntity;
import com.thx.aiplatform.website.dto.WebsiteKnowledgeEntryRequest;
import com.thx.aiplatform.website.repository.WebsiteKnowledgeRepository;
import com.thx.aiplatform.website.repository.WebsiteSettingsRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 网站助手后台的设置与知识库 CRUD API。 */
@RestController
@RequestMapping("/api/website/v1")
class WebsiteAdminController {

    private final WebsiteKnowledgeRepository knowledgeRepository;
    private final WebsiteSettingsRepository settingsRepository;

    WebsiteAdminController(
            WebsiteKnowledgeRepository knowledgeRepository,
            WebsiteSettingsRepository settingsRepository
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.settingsRepository = settingsRepository;
    }

    @PostMapping("/auth/verify")
    ResponseEntity<Void> verify() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    WebsiteAssistantSettingsEntity settings() {
        return settingsRepository.get();
    }

    @PutMapping("/settings")
    WebsiteAssistantSettingsEntity updateSettings(@Valid @RequestBody WebsiteAssistantSettingsRequest request) {
        return settingsRepository.update(request);
    }

    @GetMapping("/knowledge")
    List<WebsiteKnowledgeEntryEntity> knowledge() {
        return knowledgeRepository.findAll();
    }

    @PostMapping("/knowledge")
    WebsiteKnowledgeEntryEntity create(@Valid @RequestBody WebsiteKnowledgeEntryRequest request) {
        return knowledgeRepository.create(request);
    }

    @PutMapping("/knowledge/{id}")
    WebsiteKnowledgeEntryEntity update(@PathVariable long id, @Valid @RequestBody WebsiteKnowledgeEntryRequest request) {
        return knowledgeRepository.update(id, request);
    }

    @DeleteMapping("/knowledge/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        knowledgeRepository.delete(id);
        return ResponseEntity.noContent().build();
    }
}
