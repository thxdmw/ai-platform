package com.thx.aiplatform.server.vo;

public record ServerModelView(
        String id,
        String providerId,
        String providerName,
        String apiProtocol,
        String name,
        String modelCode,
        String reasoningEffort,
        boolean enabled,
        int sortOrder
) { }
