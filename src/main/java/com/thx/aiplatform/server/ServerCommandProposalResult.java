package com.thx.aiplatform.server;

public record ServerCommandProposalResult(
        String actionId,
        boolean success,
        String message,
        ServerCommandView command
) { }
