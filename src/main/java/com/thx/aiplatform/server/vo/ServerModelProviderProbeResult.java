package com.thx.aiplatform.server.vo;

import com.thx.aiplatform.server.model.ServerModelCatalogEntry;

import java.util.List;

public record ServerModelProviderProbeResult(boolean success, String message,
                                             List<ServerModelCatalogEntry> models) { }
