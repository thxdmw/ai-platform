package com.thx.aiplatform.server.repository;

import com.thx.aiplatform.server.model.ServerModelDefinition;
import com.thx.aiplatform.server.model.ServerModelProviderDefinition;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ServerModelProviderRepository {

    private final JdbcClient jdbc;

    ServerModelProviderRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<ServerModelProviderDefinition> findProviders() {
        return jdbc.sql("""
                SELECT id, provider_key, name, base_url, chat_completions_path, api_protocol,
                       api_key_ciphertext, enabled
                FROM server_assistant_model_provider ORDER BY created_at, name
                """).query((row, index) -> new ServerModelProviderDefinition(
                row.getString("id"), row.getString("provider_key"), row.getString("name"), row.getString("base_url"),
                row.getString("chat_completions_path"), row.getString("api_protocol"), row.getString("api_key_ciphertext"),
                row.getBoolean("enabled"))).list();
    }

    public Optional<ServerModelProviderDefinition> findProvider(String id) {
        return jdbc.sql("""
                SELECT id, provider_key, name, base_url, chat_completions_path, api_protocol,
                       api_key_ciphertext, enabled
                FROM server_assistant_model_provider WHERE id = :id
                """).param("id", id).query((row, index) -> new ServerModelProviderDefinition(
                row.getString("id"), row.getString("provider_key"), row.getString("name"), row.getString("base_url"),
                row.getString("chat_completions_path"), row.getString("api_protocol"), row.getString("api_key_ciphertext"),
                row.getBoolean("enabled"))).optional();
    }

    public void insertProvider(ServerModelProviderDefinition value) {
        jdbc.sql("""
                INSERT INTO server_assistant_model_provider
                  (id, provider_key, name, base_url, chat_completions_path, api_protocol, api_key_ciphertext, enabled)
                VALUES (:id, :providerKey, :name, :baseUrl, :path, :protocol, :key, :enabled)
                """).params(java.util.Map.of("id", value.id(), "providerKey", value.providerKey(),
                "name", value.name(), "baseUrl", value.baseUrl(), "path", value.chatCompletionsPath(),
                "protocol", value.apiProtocol(), "key", value.apiKeyCiphertext(), "enabled", value.enabled())).update();
    }

    public void updateProvider(ServerModelProviderDefinition value) {
        int count = jdbc.sql("""
                UPDATE server_assistant_model_provider
                SET provider_key=:providerKey, name=:name, base_url=:baseUrl, chat_completions_path=:path,
                    api_protocol=:protocol, api_key_ciphertext=:key, enabled=:enabled, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """).params(java.util.Map.of("id", value.id(), "providerKey", value.providerKey(),
                "name", value.name(), "baseUrl", value.baseUrl(), "path", value.chatCompletionsPath(),
                "protocol", value.apiProtocol(), "key", value.apiKeyCiphertext(), "enabled", value.enabled())).update();
        if (count == 0) throw new IllegalArgumentException("模型提供方不存在");
    }

    public void deleteProvider(String id) {
        if (jdbc.sql("DELETE FROM server_assistant_model_provider WHERE id=:id").param("id", id).update() == 0) {
            throw new IllegalArgumentException("模型提供方不存在");
        }
    }

    public List<ServerModelDefinition> findModels(String providerId, boolean onlyEnabled) {
        String sql = """
                SELECT id, provider_id, name, model_code, reasoning_effort, enabled, sort_order
                FROM server_assistant_model WHERE provider_id=:providerId
                """ + (onlyEnabled ? " AND enabled=TRUE" : "") + " ORDER BY sort_order, created_at, name";
        return jdbc.sql(sql).param("providerId", providerId).query((row, index) -> new ServerModelDefinition(
                row.getString("id"), row.getString("provider_id"), row.getString("name"),
                row.getString("model_code"), row.getString("reasoning_effort"), row.getBoolean("enabled"),
                row.getInt("sort_order"))).list();
    }

    public Optional<ServerModelDefinition> findModel(String id) {
        return jdbc.sql("""
                SELECT id, provider_id, name, model_code, reasoning_effort, enabled, sort_order
                FROM server_assistant_model WHERE id=:id
                """).param("id", id).query((row, index) -> new ServerModelDefinition(
                row.getString("id"), row.getString("provider_id"), row.getString("name"),
                row.getString("model_code"), row.getString("reasoning_effort"), row.getBoolean("enabled"),
                row.getInt("sort_order"))).optional();
    }

    public void replaceModels(String providerId, List<ServerModelDefinition> models) {
        jdbc.sql("DELETE FROM server_assistant_model WHERE provider_id=:providerId").param("providerId", providerId).update();
        for (ServerModelDefinition model : models) {
            jdbc.sql("""
                    INSERT INTO server_assistant_model
                      (id, provider_id, name, model_code, reasoning_effort, enabled, sort_order)
                    VALUES (:id, :providerId, :name, :modelCode, :reasoningEffort, :enabled, :sortOrder)
                    """).param("id", model.id()).param("providerId", providerId).param("name", model.name())
                    .param("modelCode", model.modelCode()).param("reasoningEffort", model.reasoningEffort())
                    .param("enabled", model.enabled()).param("sortOrder", model.sortOrder()).update();
        }
    }
}
