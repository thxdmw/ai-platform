package com.thx.aiplatform.website.service;

import com.thx.aiplatform.website.entity.WebsiteKnowledgeEntryEntity;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * 网站知识的轻量检索器。它不把全库塞给模型，而是按标题、FAQ 问题、
 * 关键词和正文的命中程度召回最相关的少量条目。对个人站点的小型知识库，
 * 这比引入向量数据库更便宜、可解释，同时保留了 RAG「先检索再生成」的关键边界。
 */
@Component
public class WebsiteKnowledge {

    private static final int MAX_ENTRIES = 8;
    private static final int MAX_CONTEXT_CHARACTERS = 6_000;

    private final WebsiteKnowledgeService repository;

    public WebsiteKnowledge(WebsiteKnowledgeService repository) {
        this.repository = repository;
    }

    public String contentFor(String query) {
        String normalizedQuery = normalize(query);
        Set<String> queryBigrams = bigrams(normalizedQuery);
        List<ScoredEntry> scored = repository.findEnabled().stream()
                .map(entry -> new ScoredEntry(entry, score(entry, normalizedQuery, queryBigrams)))
                .sorted(Comparator.comparingInt(ScoredEntry::score).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (ScoredEntry item) -> item.entry().getPriority()).reversed()))
                .toList();

        List<WebsiteKnowledgeEntryEntity> selected = scored.stream()
                .filter(item -> item.score() >= 8)
                .limit(MAX_ENTRIES)
                .map(ScoredEntry::entry)
                .toList();
        if (selected.isEmpty()) {
            // 没有词面命中时仍给出两条高优先级站点概况，让模型能回答「这是什么网站」之类泛问题。
            selected = scored.stream().limit(2).map(ScoredEntry::entry).toList();
        }
        return renderWithinBudget(selected);
    }

    private int score(WebsiteKnowledgeEntryEntity entry, String query, Set<String> queryBigrams) {
        String title = normalize(entry.getTitle());
        String question = normalize(entry.getQuestion());
        String keywords = normalize(entry.getKeywords());
        String content = normalize(entry.getContent());
        int score = 0;
        if (!query.isEmpty() && question.equals(query)) score += 120;
        if (!query.isEmpty() && title.contains(query)) score += 50;
        if (!query.isEmpty() && question.contains(query)) score += 60;
        if (!query.isEmpty() && keywords.contains(query)) score += 45;
        if (!query.isEmpty() && content.contains(query)) score += 25;
        score += overlap(queryBigrams, bigrams(title + question)) * 5;
        score += overlap(queryBigrams, bigrams(keywords)) * 4;
        score += Math.min(overlap(queryBigrams, bigrams(content)), 12);
        return score;
    }

    private int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String value : left) if (right.contains(value)) count++;
        return count;
    }

    private Set<String> bigrams(String value) {
        Set<String> values = new HashSet<>();
        String compact = value.replaceAll("\\s+", "");
        if (compact.length() == 1) values.add(compact);
        for (int index = 0; index < compact.length() - 1; index++) {
            values.add(compact.substring(index, index + 2));
        }
        return values;
    }

    private String renderWithinBudget(List<WebsiteKnowledgeEntryEntity> entries) {
        StringBuilder context = new StringBuilder();
        for (WebsiteKnowledgeEntryEntity entry : entries) {
            String block = entry.getEntryType().name().equals("FAQ")
                    ? "[FAQ] %s\n问：%s\n答：%s\n\n".formatted(entry.getTitle(), entry.getQuestion(), entry.getContent())
                    : "[资料] %s\n%s\n\n".formatted(entry.getTitle(), entry.getContent());
            int remaining = MAX_CONTEXT_CHARACTERS - context.length();
            if (remaining <= 0) break;
            if (block.length() > remaining) {
                context.append(block, 0, remaining);
                break;
            }
            context.append(block);
        }
        return context.isEmpty() ? "暂无可用的网站资料。" : context.toString().trim();
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private record ScoredEntry(WebsiteKnowledgeEntryEntity entry, int score) {
    }
}
