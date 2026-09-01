package com.thx.aiplatform.server.model;

public record ServerModelProviderDefinition(
        String id,
        String name,
        String baseUrl,
        String chatCompletionsPath,
        String apiKeyCiphertext,
        boolean enabled
) { }
