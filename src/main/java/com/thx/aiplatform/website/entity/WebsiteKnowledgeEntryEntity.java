package com.thx.aiplatform.website.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.thx.aiplatform.website.enums.WebsiteKnowledgeEntryType;

import java.time.LocalDateTime;

/**
 * website_knowledge_entries 表的持久化实体，同时充当服务层与后台 API 的返回形态
 * （网格中不再维护与实体重复的 WebsiteKnowledgeEntry 记录）。id 是数据库自增标识列，
 * 插入后由 MyBatis-Plus 回填。entryType 枚举与库中存储的字符串（INFO/FAQ）按 name()
 * 一致映射，无需 @EnumValue。
 */
@TableName("website_knowledge_entries")
public class WebsiteKnowledgeEntryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private WebsiteKnowledgeEntryType entryType;
    private String title;
    private String question;
    private String content;
    private String keywords;
    private boolean enabled;
    private int priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public WebsiteKnowledgeEntryType getEntryType() { return entryType; }

    public void setEntryType(WebsiteKnowledgeEntryType entryType) { this.entryType = entryType; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getQuestion() { return question; }

    public void setQuestion(String question) { this.question = question; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getKeywords() { return keywords; }

    public void setKeywords(String keywords) { this.keywords = keywords; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getPriority() { return priority; }

    public void setPriority(int priority) { this.priority = priority; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 无参构造供 MyBatis-Plus 反射实例化（全参构造会覆盖默认无参构造，必须显式保留）。 */
    public WebsiteKnowledgeEntryEntity() { }

    /** 便捷全参构造（顺序对齐原 WebsiteKnowledgeEntry 记录），供服务层与测试构建。 */
    public WebsiteKnowledgeEntryEntity(Long id, WebsiteKnowledgeEntryType entryType, String title, String question,
                                       String content, String keywords, boolean enabled, int priority,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.entryType = entryType;
        this.title = title;
        this.question = question;
        this.content = content;
        this.keywords = keywords;
        this.enabled = enabled;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}