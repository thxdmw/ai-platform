package com.thx.aiplatform.server.model;

/** 临时命令审批结果；continuationId 用于从同一 ReAct 任务的暂停点继续。 */
public record ServerOperationDecisionResult(
        String actionId,
        String status,
        String message,
        SshExecutionResult execution,
        String continuationId
) { }
