package com.thx.aiplatform.website.repository;

import com.thx.aiplatform.website.model.WebsiteKnowledgeEntry;
import com.thx.aiplatform.website.model.WebsiteKnowledgeEntryRequest;
import com.thx.aiplatform.website.model.WebsiteKnowledgeEntryType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 网站知识条目的 JDBC 持久化边界。 */
@Repository
public class WebsiteKnowledgeRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, entry_type, title, question, content, keywords, enabled, priority, created_at, updated_at
            FROM website_knowledge_entries
            """;

    private final JdbcTemplate jdbcTemplate;

    public WebsiteKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WebsiteKnowledgeEntry> findAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY priority DESC, updated_at DESC", this::map);
    }

    public List<WebsiteKnowledgeEntry> findEnabled() {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE enabled = TRUE ORDER BY priority DESC, updated_at DESC", this::map);
    }

    public Optional<WebsiteKnowledgeEntry> findById(long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    public WebsiteKnowledgeEntry create(WebsiteKnowledgeEntryRequest request) {
        validate(request);
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO website_knowledge_entries
                        (entry_type, title, question, content, keywords, enabled, priority, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bind(statement, request, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("知识条目保存成功但未返回编号");
        return findById(key.longValue()).orElseThrow();
    }

    public WebsiteKnowledgeEntry update(long id, WebsiteKnowledgeEntryRequest request) {
        validate(request);
        int affected = jdbcTemplate.update("""
                UPDATE website_knowledge_entries
                SET entry_type = ?, title = ?, question = ?, content = ?, keywords = ?, enabled = ?, priority = ?, updated_at = ?
                WHERE id = ?
                """, request.entryType().name(), normalized(request.title()), normalized(request.question()),
                normalized(request.content()), normalized(request.keywords()), request.enabled(), request.priority(),
                Timestamp.valueOf(LocalDateTime.now()), id);
        if (affected == 0) throw new IllegalArgumentException("知识条目不存在");
        return findById(id).orElseThrow();
    }

    public void delete(long id) {
        if (jdbcTemplate.update("DELETE FROM website_knowledge_entries WHERE id = ?", id) == 0) {
            throw new IllegalArgumentException("知识条目不存在");
        }
    }

    private void bind(PreparedStatement statement, WebsiteKnowledgeEntryRequest request, LocalDateTime now)
            throws java.sql.SQLException {
        statement.setString(1, request.entryType().name());
        statement.setString(2, normalized(request.title()));
        statement.setString(3, normalized(request.question()));
        statement.setString(4, normalized(request.content()));
        statement.setString(5, normalized(request.keywords()));
        statement.setBoolean(6, request.enabled());
        statement.setInt(7, request.priority());
        statement.setTimestamp(8, Timestamp.valueOf(now));
        statement.setTimestamp(9, Timestamp.valueOf(now));
    }

    private void validate(WebsiteKnowledgeEntryRequest request) {
        if (request.entryType() == WebsiteKnowledgeEntryType.FAQ
                && (request.question() == null || request.question().isBlank())) {
            throw new IllegalArgumentException("FAQ 必须填写常见问题");
        }
    }

    private WebsiteKnowledgeEntry map(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new WebsiteKnowledgeEntry(
                resultSet.getLong("id"),
                WebsiteKnowledgeEntryType.valueOf(resultSet.getString("entry_type")),
                resultSet.getString("title"),
                resultSet.getString("question"),
                resultSet.getString("content"),
                resultSet.getString("keywords"),
                resultSet.getBoolean("enabled"),
                resultSet.getInt("priority"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
