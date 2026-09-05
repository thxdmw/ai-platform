package com.thx.aiplatform.website.repository;

import com.thx.aiplatform.website.model.WebsiteAssistantSettings;
import com.thx.aiplatform.website.model.WebsiteAssistantSettingsRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 单例网站助手设置的持久化边界。 */
@Repository
public class WebsiteSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public WebsiteSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WebsiteAssistantSettings get() {
        return jdbcTemplate.queryForObject("""
                SELECT assistant_name, welcome_message, prompt_addition, enabled, updated_at
                FROM website_assistant_settings WHERE id = 1
                """, (resultSet, rowNumber) -> new WebsiteAssistantSettings(
                resultSet.getString("assistant_name"),
                resultSet.getString("welcome_message"),
                resultSet.getString("prompt_addition"),
                resultSet.getBoolean("enabled"),
                resultSet.getTimestamp("updated_at").toLocalDateTime()
        ));
    }

    public WebsiteAssistantSettings update(WebsiteAssistantSettingsRequest request) {
        jdbcTemplate.update("""
                UPDATE website_assistant_settings
                SET assistant_name = ?, welcome_message = ?, prompt_addition = ?, enabled = ?, updated_at = ?
                WHERE id = 1
                """, request.assistantName().trim(), request.welcomeMessage().trim(),
                request.promptAddition() == null ? "" : request.promptAddition().trim(), request.enabled(),
                Timestamp.valueOf(LocalDateTime.now()));
        return get();
    }
}
