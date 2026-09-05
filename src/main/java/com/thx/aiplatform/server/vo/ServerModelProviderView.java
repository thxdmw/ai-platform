package com.thx.aiplatform.server.vo;

import java.util.List;

public record ServerModelProviderView(
        String id,
        String providerKey,
        String name,
        String baseUrl,
        String apiProtocol,
        boolean apiKeyConfigured,
        boolean enabled,
        List<ServerModelView> models
) { }
