package com.thx.aiplatform.server;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

final class ServerOperationTool {

    private final String conversationId;
    private final ServerOperationService operationService;

    ServerOperationTool(String conversationId, ServerOperationService operationService) {
        this.conversationId = conversationId;
        this.operationService = operationService;
    }

    @Tool(description = "生成服务器操作确认选项。仅支持重启白名单内的 systemd 服务或 Docker 容器；工具不会直接执行，必须由用户在界面确认")
    public String proposeOperation(
            @ToolParam(description = "服务器 ID") String serverId,
            @ToolParam(description = "操作类型：RESTART_SERVICE 或 RESTART_CONTAINER") String operation,
            @ToolParam(description = "服务名或容器名，必须来自服务器白名单") String target,
            @ToolParam(description = "执行原因和已完成的诊断依据") String reason
    ) {
        PendingServerOperationView pending = operationService.prepare(conversationId, serverId, operation, target, reason);
        return "已生成“" + pending.operation() + " " + pending.target() + "”操作选项，等待用户确认。"
                + "不要声称命令已经执行，也不要输出内部操作编号。";
    }
}
