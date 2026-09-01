ALTER TABLE server_assistant_command
    ADD COLUMN parameter_schema TEXT NOT NULL DEFAULT '[]';

CREATE TABLE server_assistant_model_provider (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    base_url VARCHAR(500) NOT NULL,
    chat_completions_path VARCHAR(200) NOT NULL DEFAULT '/v1/chat/completions',
    api_key_ciphertext TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE server_assistant_model (
    id VARCHAR(36) PRIMARY KEY,
    provider_id VARCHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    model_code VARCHAR(200) NOT NULL,
    reasoning_effort VARCHAR(24),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_server_assistant_model_provider
        FOREIGN KEY (provider_id) REFERENCES server_assistant_model_provider(id) ON DELETE CASCADE,
    CONSTRAINT uk_server_assistant_model_code UNIQUE (provider_id, model_code)
);

CREATE INDEX idx_server_assistant_model_enabled
    ON server_assistant_model(provider_id, enabled, sort_order);
