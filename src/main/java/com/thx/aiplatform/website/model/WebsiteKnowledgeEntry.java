package com.thx.aiplatform.website.model;

import java.time.LocalDateTime;

/** 后台可维护的单条网站知识。 */
public record WebsiteKnowledgeEntry(
        long id,
        WebsiteKnowledgeEntryType entryType,
        String title,
        String question,
        String content,
        String keywords,
        boolean enabled,
        int priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
