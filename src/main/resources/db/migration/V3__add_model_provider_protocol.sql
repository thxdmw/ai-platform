ALTER TABLE server_assistant_model_provider
    ADD COLUMN provider_key VARCHAR(64);

UPDATE server_assistant_model_provider
SET provider_key = CONCAT('provider-', REPLACE(id, '-', ''));

ALTER TABLE server_assistant_model_provider
    ALTER COLUMN provider_key SET NOT NULL;

ALTER TABLE server_assistant_model_provider
    ADD CONSTRAINT uk_server_assistant_model_provider_key UNIQUE (provider_key);

ALTER TABLE server_assistant_model_provider
    ADD COLUMN api_protocol VARCHAR(40) NOT NULL DEFAULT 'openai-completions';
