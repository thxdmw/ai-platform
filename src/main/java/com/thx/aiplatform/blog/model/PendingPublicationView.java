package com.thx.aiplatform.blog.model;

import java.time.Instant;

/**
 * 面向页面与 SSE 的待发布候选视图。刻意只暴露摘要、长度与过期时间而不带正文全量：
 * 正文在管理员确认前只应存在于服务端 pending 中，避免大段草稿被反复搬进会话与网络传输。
 */
public record PendingPublicationView(
        String actionId,
        String title,
        String description,
        String categoryId,
        String tagIds,
        int contentLength,
        Instant expiresAt,
        String status
) {
}
