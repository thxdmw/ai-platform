package com.thx.aiplatform.blog.model;

/**
 * 发布操作的返回视图：success 区分「已发布」与「结果不确定」，
 * upstreamResponse 由服务端截断后仅用于页面回显，不进入模型会话。
 */
public record PublicationResult(
        String actionId,
        boolean success,
        String message,
        String upstreamResponse
) {
}
