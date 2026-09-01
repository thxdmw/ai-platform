package com.thx.aiplatform.server.model;

/**
 * 命令的领域实体（库中存储形态），与对外视图 {@link ServerCommandView} 分离：这里持有
 * 枚举形式的 riskLevel 供内部逻辑（风险分级、二次确认）直接判断，视图中则是字符串。
 */
public record ServerCommandDefinition(
        String id,
        String serverId,
        String name,
        String description,
        String commandText,
        ServerCommandRisk riskLevel,
        boolean enabled,
        int sortOrder
) { }
