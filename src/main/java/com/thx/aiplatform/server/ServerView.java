package com.thx.aiplatform.server;

public record ServerView(
        String id,
        String name,
        String host,
        int port,
        String username,
        String authenticationType,
        String hostKey,
        boolean enabled,
        boolean credentialConfigured
) { }
