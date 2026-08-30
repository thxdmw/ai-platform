package com.thx.aiplatform.blog;

public record PublicationResult(
        String actionId,
        boolean success,
        String message,
        String upstreamResponse
) {
}
