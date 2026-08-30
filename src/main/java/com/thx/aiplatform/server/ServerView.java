package com.thx.aiplatform.server;

import java.util.List;

public record ServerView(
        String id,
        String name,
        String host,
        int port,
        String username,
        List<String> allowedServices,
        List<String> allowedContainers
) { }
