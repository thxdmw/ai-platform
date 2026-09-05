package com.thx.aiplatform.website.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * website_assistant_settings 表的持久化实体，同时充当服务层与后台 API 的返回形态
 * （网格中不再维护与实体重复的 WebsiteAssistantSettings 记录）。该表只有 Flyway
 * 种子数据写死的一行 id=1，运行时只更新不新增，因此主键采用手工赋值（INPUT）。
 */
@TableName("website_assistant_settings")
public class WebsiteAssistantSettingsEntity {

    @TableId(type = IdType.INPUT)
    private Integer id;
    private String assistantName;
    private String welcomeMessage;
    private String promptAddition;
    private boolean enabled;
    private LocalDateTime updatedAt;

    public Integer getId() { return id; }

    public void setId(Integer id) { this.id = id; }

    public String getAssistantName() { return assistantName; }

    public void setAssistantName(String assistantName) { this.assistantName = assistantName; }

    public String getWelcomeMessage() { return welcomeMessage; }

    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }

    public String getPromptAddition() { return promptAddition; }

    public void setPromptAddition(String promptAddition) { this.promptAddition = promptAddition; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 无参构造供 MyBatis-Plus 反射实例化（全参构造会覆盖默认无参构造，必须显式保留）。 */
    public WebsiteAssistantSettingsEntity() { }

    /** 便捷全参构造（顺序对齐原 WebsiteAssistantSettings 记录），供服务层与测试构建。 */
    public WebsiteAssistantSettingsEntity(String assistantName, String welcomeMessage, String promptAddition,
                                          boolean enabled, LocalDateTime updatedAt) {
        this.assistantName = assistantName;
        this.welcomeMessage = welcomeMessage;
        this.promptAddition = promptAddition;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }
}