package com.thx.aiplatform.server.model;

/**
 * 「命令添加确认」的结果：命令落库后的完整视图连同 continuationId 一起返回，页面拿到后
 * 可直接调用 /messages/continue 让模型基于「已确认添加」的可信事件继续原任务。
 */
public record ServerCommandProposalResult(
        String actionId,
        boolean success,
        String message,
        ServerCommandView command,
        String continuationId
) { }
