package com.thx.aiplatform.server;

import java.time.Instant;

public record PendingServerCommandProposalView(
        String actionId,
        String serverId,
        String serverName,
        String commandName,
        String commandDescription,
        String commandPreview,
        String riskLevel,
        String reason,
        Instant expiresAt,
        String status,
        String actionType
) { }
