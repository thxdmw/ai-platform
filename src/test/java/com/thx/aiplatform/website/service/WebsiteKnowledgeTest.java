package com.thx.aiplatform.website.service;

import com.thx.aiplatform.website.model.WebsiteKnowledgeEntry;
import com.thx.aiplatform.website.model.WebsiteKnowledgeEntryType;
import com.thx.aiplatform.website.repository.WebsiteKnowledgeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 轻量召回测试：高相关 FAQ 应进入上下文，无关资料不应占用 token。 */
class WebsiteKnowledgeTest {

    @Test
    void 按问题和关键词召回相关知识() {
        WebsiteKnowledgeRepository repository = mock(WebsiteKnowledgeRepository.class);
        when(repository.findEnabled()).thenReturn(List.of(
                entry(1, WebsiteKnowledgeEntryType.FAQ, "博客入口", "博客在哪里？", "点击首页博客卡片", "博客,文章", 80),
                entry(2, WebsiteKnowledgeEntryType.INFO, "游戏项目", "", "这里有一些小游戏", "游戏", 100)
        ));

        String context = new WebsiteKnowledge(repository).contentFor("我想看博客文章，入口在哪里？");

        assertThat(context).contains("博客入口", "点击首页博客卡片");
        assertThat(context).doesNotContain("游戏项目");
    }

    private WebsiteKnowledgeEntry entry(
            long id, WebsiteKnowledgeEntryType type, String title, String question,
            String content, String keywords, int priority
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new WebsiteKnowledgeEntry(id, type, title, question, content, keywords, true, priority, now, now);
    }
}
