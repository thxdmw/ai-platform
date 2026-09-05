package com.thx.aiplatform.server.vo;

import java.time.Instant;

/**
 * 待确认「命令添加提议」对页面的视图：把内部提议里嵌套的服务器对象展开为 serverId/
 * serverName，暴露风险等级字符串与截止时间，前端直接渲染确认卡片即可，无需感知内部
 * 内存存储结构。
 */
public record PendingServerCommandProposalView(
        String actionId,
        String serverId,
        String serverName,
        String commandName,
        String commandDescription,
        String commandPreview,
        String parameterSchema,
        String riskLevel,
        String reason,
        Instant expiresAt,
        String status,
        String actionType
) { }
