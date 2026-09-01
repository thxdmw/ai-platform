CREATE TABLE server_assistant_server (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(128) NOT NULL,
    authentication_type VARCHAR(24) NOT NULL,
    credential_ciphertext TEXT NOT NULL,
    passphrase_ciphertext TEXT,
    host_key TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE server_assistant_command (
    id VARCHAR(36) PRIMARY KEY,
    server_id VARCHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    command_text TEXT NOT NULL,
    risk_level VARCHAR(24) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_server_assistant_command_server
        FOREIGN KEY (server_id) REFERENCES server_assistant_server(id) ON DELETE CASCADE,
    CONSTRAINT uk_server_assistant_command_name UNIQUE (server_id, name)
);

CREATE INDEX idx_server_assistant_server_enabled
    ON server_assistant_server(enabled);

CREATE INDEX idx_server_assistant_command_server_enabled
    ON server_assistant_command(server_id, enabled, sort_order);
