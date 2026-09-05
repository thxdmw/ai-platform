package com.thx.aiplatform.website.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.thx.aiplatform.website.dto.WebsiteKnowledgeEntryRequest;
import com.thx.aiplatform.website.entity.WebsiteKnowledgeEntryEntity;
import com.thx.aiplatform.website.enums.WebsiteKnowledgeEntryType;
import com.thx.aiplatform.website.repository.WebsiteKnowledgeMapper;
import com.thx.aiplatform.website.service.WebsiteKnowledgeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 网站知识条目的业务实现：校验、排序召回与增删改，经 MyBatis-Plus 映射器落库。 */
@Service
public class WebsiteKnowledgeServiceImpl implements WebsiteKnowledgeService {

    private final WebsiteKnowledgeMapper mapper;

    public WebsiteKnowledgeServiceImpl(WebsiteKnowledgeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<WebsiteKnowledgeEntryEntity> findAll() {
        return mapper.selectList(Wrappers.<WebsiteKnowledgeEntryEntity>lambdaQuery()
                .orderByDesc(WebsiteKnowledgeEntryEntity::getPriority)
                .orderByDesc(WebsiteKnowledgeEntryEntity::getUpdatedAt));
    }

    @Override
    public List<WebsiteKnowledgeEntryEntity> findEnabled() {
        return mapper.selectList(Wrappers.<WebsiteKnowledgeEntryEntity>lambdaQuery()
                .eq(WebsiteKnowledgeEntryEntity::isEnabled, true)
                .orderByDesc(WebsiteKnowledgeEntryEntity::getPriority)
                .orderByDesc(WebsiteKnowledgeEntryEntity::getUpdatedAt));
    }

    @Override
    public Optional<WebsiteKnowledgeEntryEntity> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public WebsiteKnowledgeEntryEntity create(WebsiteKnowledgeEntryRequest request) {
        validate(request);
        WebsiteKnowledgeEntryEntity entity = new WebsiteKnowledgeEntryEntity();
        fill(entity, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return findById(entity.getId()).orElseThrow(() -> new IllegalStateException("知识条目保存成功但未返回编号"));
    }

    @Override
    public WebsiteKnowledgeEntryEntity update(long id, WebsiteKnowledgeEntryRequest request) {
        validate(request);
        WebsiteKnowledgeEntryEntity entity = new WebsiteKnowledgeEntryEntity();
        entity.setId(id);
        fill(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
        if (mapper.updateById(entity) == 0) throw new IllegalArgumentException("知识条目不存在");
        return findById(id).orElseThrow();
    }

    @Override
    public void delete(long id) {
        if (mapper.deleteById(id) == 0) throw new IllegalArgumentException("知识条目不存在");
    }

    private void fill(WebsiteKnowledgeEntryEntity entity, WebsiteKnowledgeEntryRequest request) {
        entity.setEntryType(request.entryType());
        entity.setTitle(normalized(request.title()));
        entity.setQuestion(normalized(request.question()));
        entity.setContent(normalized(request.content()));
        entity.setKeywords(normalized(request.keywords()));
        entity.setEnabled(request.enabled());
        entity.setPriority(request.priority());
    }

    private void validate(WebsiteKnowledgeEntryRequest request) {
        if (request.entryType() == WebsiteKnowledgeEntryType.FAQ
                && (request.question() == null || request.question().isBlank())) {
            throw new IllegalArgumentException("FAQ 必须填写常见问题");
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}