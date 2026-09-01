package com.thx.aiplatform.server;

import java.time.Instant;

public record PendingServerOperationView(
        String actionId,
        String serverId,
        String serverName,
        String commandId,
        String commandName,
        String commandPreview,
        String reason,
        Instant expiresAt,
        String status
) { }
