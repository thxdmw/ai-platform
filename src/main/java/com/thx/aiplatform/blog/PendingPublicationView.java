package com.thx.aiplatform.blog;

import java.time.Instant;

public record PendingPublicationView(
        String actionId,
        String title,
        String description,
        int contentLength,
        Instant expiresAt,
        String status
) {
}
