package com.thx.aiplatform.server.model;

/**
 * 命令的对外视图：面向页面展示。这里暴露完整 commandText 供用户复核，但工具输出（
 * {@code ServerCommandTool#listCommands}）刻意不包含它，防止模型抄写或改造命令。
 */
public record ServerCommandView(
        String id,
        String serverId,
        String name,
        String description,
        String commandText,
        String parameterSchema,
        String riskLevel,
        boolean enabled,
        int sortOrder
) { }
