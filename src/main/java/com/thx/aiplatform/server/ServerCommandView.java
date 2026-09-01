package com.thx.aiplatform.server;

public record ServerCommandView(
        String id,
        String serverId,
        String name,
        String description,
        String commandText,
        String riskLevel,
        boolean enabled,
        int sortOrder
) { }
