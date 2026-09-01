package com.thx.aiplatform.server.model;

import java.util.List;

public record ServerModelProviderProbeResult(boolean success, String message,
                                             List<ServerModelCatalogEntry> models) { }
