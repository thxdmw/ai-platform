package com.thx.aiplatform.server.vo;

import com.thx.aiplatform.server.model.SshExecutionResult;

/**
 * 危险操作执行结果：success 表示远端执行是否成功，execution 携带完整执行明细（失败或
 * 结果不确定时为 null）；continuationId 供前端续跑，让模型基于真实结果继续任务。
 */
public record ServerOperationResult(
        String actionId,
        boolean success,
        String message,
        SshExecutionResult execution,
        String continuationId
) { }
