package com.thx.aiplatform.server.model;

public record ServerModelProviderDefinition(
        String id,
        String providerKey,
        String name,
        String baseUrl,
        String chatCompletionsPath,
        String apiProtocol,
        String apiKeyCiphertext,
        boolean enabled
) { }
