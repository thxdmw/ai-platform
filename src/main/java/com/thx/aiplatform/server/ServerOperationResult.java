package com.thx.aiplatform.server;

public record ServerOperationResult(
        String actionId,
        boolean success,
        String message,
        SshExecutionResult execution
) { }
