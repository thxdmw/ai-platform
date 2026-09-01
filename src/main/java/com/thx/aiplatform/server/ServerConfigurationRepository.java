package com.thx.aiplatform.server;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
class ServerConfigurationRepository {

    private final JdbcClient jdbc;

    ServerConfigurationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<ServerDefinition> findServers(boolean onlyEnabled) {
        String sql = """
                SELECT id, name, host, port, username, authentication_type, credential_ciphertext,
                       passphrase_ciphertext, host_key, enabled
                FROM server_assistant_server
                """ + (onlyEnabled ? " WHERE enabled = TRUE" : "") + " ORDER BY created_at, name";
        return jdbc.sql(sql).query(this::mapServer).list();
    }

    Optional<ServerDefinition> findServer(String id) {
        return jdbc.sql("""
                        SELECT id, name, host, port, username, authentication_type, credential_ciphertext,
                               passphrase_ciphertext, host_key, enabled
                        FROM server_assistant_server WHERE id = :id
                        """)
                .param("id", id).query(this::mapServer).optional();
    }

    void insertServer(ServerDefinition server) {
        jdbc.sql("""
                        INSERT INTO server_assistant_server
                          (id, name, host, port, username, authentication_type, credential_ciphertext,
                           passphrase_ciphertext, host_key, enabled)
                        VALUES
                          (:id, :name, :host, :port, :username, :authenticationType, :credentialCiphertext,
                           :passphraseCiphertext, :hostKey, :enabled)
                        """)
                .params(serverParameters(server)).update();
    }

    void updateServer(ServerDefinition server) {
        int updated = jdbc.sql("""
                        UPDATE server_assistant_server
                        SET name = :name, host = :host, port = :port, username = :username,
                            authentication_type = :authenticationType,
                            credential_ciphertext = :credentialCiphertext,
                            passphrase_ciphertext = :passphraseCiphertext,
                            host_key = :hostKey, enabled = :enabled, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """)
                .params(serverParameters(server)).update();
        if (updated == 0) throw new IllegalArgumentException("服务器配置不存在");
    }

    void deleteServer(String id) {
        if (jdbc.sql("DELETE FROM server_assistant_server WHERE id = :id").param("id", id).update() == 0) {
            throw new IllegalArgumentException("服务器配置不存在");
        }
    }

    List<ServerCommandDefinition> findCommands(String serverId, boolean onlyEnabled) {
        String sql = """
                SELECT id, server_id, name, description, command_text, risk_level, enabled, sort_order
                FROM server_assistant_command WHERE server_id = :serverId
                """ + (onlyEnabled ? " AND enabled = TRUE" : "") + " ORDER BY sort_order, created_at, name";
        return jdbc.sql(sql).param("serverId", serverId).query(this::mapCommand).list();
    }

    Optional<ServerCommandDefinition> findCommand(String id) {
        return jdbc.sql("""
                        SELECT id, server_id, name, description, command_text, risk_level, enabled, sort_order
                        FROM server_assistant_command WHERE id = :id
                        """)
                .param("id", id).query(this::mapCommand).optional();
    }

    void insertCommand(ServerCommandDefinition command) {
        jdbc.sql("""
                        INSERT INTO server_assistant_command
                          (id, server_id, name, description, command_text, risk_level, enabled, sort_order)
                        VALUES
                          (:id, :serverId, :name, :description, :commandText, :riskLevel, :enabled, :sortOrder)
                        """)
                .params(commandParameters(command)).update();
    }

    void updateCommand(ServerCommandDefinition command) {
        int updated = jdbc.sql("""
                        UPDATE server_assistant_command
                        SET name = :name, description = :description, command_text = :commandText,
                            risk_level = :riskLevel, enabled = :enabled, sort_order = :sortOrder,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id AND server_id = :serverId
                        """)
                .params(commandParameters(command)).update();
        if (updated == 0) throw new IllegalArgumentException("服务器命令不存在");
    }

    void deleteCommand(String id) {
        if (jdbc.sql("DELETE FROM server_assistant_command WHERE id = :id").param("id", id).update() == 0) {
            throw new IllegalArgumentException("服务器命令不存在");
        }
    }

    private java.util.Map<String, Object> serverParameters(ServerDefinition server) {
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("id", server.id());
        values.put("name", server.name());
        values.put("host", server.host());
        values.put("port", server.port());
        values.put("username", server.username());
        values.put("authenticationType", server.authenticationType().name());
        values.put("credentialCiphertext", server.credentialCiphertext());
        values.put("passphraseCiphertext", server.passphraseCiphertext());
        values.put("hostKey", server.hostKey());
        values.put("enabled", server.enabled());
        return values;
    }

    private java.util.Map<String, Object> commandParameters(ServerCommandDefinition command) {
        return java.util.Map.of(
                "id", command.id(), "serverId", command.serverId(), "name", command.name(),
                "description", command.description(), "commandText", command.commandText(),
                "riskLevel", command.riskLevel().name(), "enabled", command.enabled(), "sortOrder", command.sortOrder());
    }

    private ServerDefinition mapServer(ResultSet result, int row) throws SQLException {
        return new ServerDefinition(
                result.getString("id"), result.getString("name"), result.getString("host"), result.getInt("port"),
                result.getString("username"), ServerAuthenticationType.parse(result.getString("authentication_type")),
                result.getString("credential_ciphertext"), result.getString("passphrase_ciphertext"),
                result.getString("host_key"), result.getBoolean("enabled"));
    }

    private ServerCommandDefinition mapCommand(ResultSet result, int row) throws SQLException {
        return new ServerCommandDefinition(
                result.getString("id"), result.getString("server_id"), result.getString("name"),
                result.getString("description"), result.getString("command_text"),
                ServerCommandRisk.parse(result.getString("risk_level")), result.getBoolean("enabled"),
                result.getInt("sort_order"));
    }
}
