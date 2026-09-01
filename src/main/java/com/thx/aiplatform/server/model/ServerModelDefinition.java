package com.thx.aiplatform.server.model;

public record ServerModelDefinition(
        String id,
        String providerId,
        String name,
        String modelCode,
        String reasoningEffort,
        boolean enabled,
        int sortOrder
) { }
