package com.thx.aiplatform.website.repository;

import com.thx.aiplatform.website.dto.WebsiteKnowledgeEntryRequest;
import com.thx.aiplatform.website.enums.WebsiteKnowledgeEntryType;
import com.thx.aiplatform.website.service.WebsiteKnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 知识库持久化回归测试，覆盖后台会执行的完整增删改链路。 */
@SpringBootTest
@Transactional
class WebsiteKnowledgeRepositoryTest {

    @Autowired
    private WebsiteKnowledgeService repository;

    @Test
    void 知识条目可以新增编辑和删除() {
        var created = repository.create(new WebsiteKnowledgeEntryRequest(
                WebsiteKnowledgeEntryType.FAQ, "联系站长", "如何联系站长？",
                "请使用首页的联系入口。", "联系,站长", true, 80));

        var updated = repository.update(created.getId(), new WebsiteKnowledgeEntryRequest(
                WebsiteKnowledgeEntryType.FAQ, "联系方式", "怎样联系站长？",
                "请查看首页联系卡片。", "联系,站长", false, 90));

        assertThat(updated.getTitle()).isEqualTo("联系方式");
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getPriority()).isEqualTo(90);

        repository.delete(created.getId());
        assertThat(repository.findById(created.getId())).isEmpty();
    }
}
