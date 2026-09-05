package com.thx.aiplatform.server.vo;

import java.time.Instant;

/**
 * 待确认「危险操作」对页面的视图：命令以 ID + 名称 + 文本预览形式给出，配合 reason
 * 与过期时间渲染确认卡片；actionType 固定为 EXECUTE_COMMAND，前端据此决定按钮文案。
 */
public record PendingServerOperationView(
        String actionId,
        String serverId,
        String serverName,
        String commandId,
        String commandName,
        String commandPreview,
        String workingDirectory,
        boolean temporary,
        String reason,
        Instant expiresAt,
        String status,
        String actionType
) { }
