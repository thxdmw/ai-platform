package com.thx.aiplatform.server;

record ServerCommandDefinition(
        String id,
        String serverId,
        String name,
        String description,
        String commandText,
        ServerCommandRisk riskLevel,
        boolean enabled,
        int sortOrder
) { }
