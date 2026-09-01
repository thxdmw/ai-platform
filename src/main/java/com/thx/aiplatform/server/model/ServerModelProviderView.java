package com.thx.aiplatform.server.model;

import java.util.List;

public record ServerModelProviderView(
        String id,
        String name,
        String baseUrl,
        String chatCompletionsPath,
        boolean apiKeyConfigured,
        boolean enabled,
        List<ServerModelView> models
) { }
