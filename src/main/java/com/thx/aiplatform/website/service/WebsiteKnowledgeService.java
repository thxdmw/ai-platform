package com.thx.aiplatform.website.service;

import com.thx.aiplatform.website.dto.WebsiteKnowledgeEntryRequest;
import com.thx.aiplatform.website.entity.WebsiteKnowledgeEntryEntity;

import java.util.List;
import java.util.Optional;

/** 网站知识条目的管理服务：后台 CRUD 与公开召回所需的启用集查询。 */
public interface WebsiteKnowledgeService {

    List<WebsiteKnowledgeEntryEntity> findAll();

    List<WebsiteKnowledgeEntryEntity> findEnabled();

    Optional<WebsiteKnowledgeEntryEntity> findById(long id);

    WebsiteKnowledgeEntryEntity create(WebsiteKnowledgeEntryRequest request);

    WebsiteKnowledgeEntryEntity update(long id, WebsiteKnowledgeEntryRequest request);

    void delete(long id);
}