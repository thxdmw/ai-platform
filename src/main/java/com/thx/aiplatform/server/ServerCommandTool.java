package com.thx.aiplatform.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

final class ServerCommandTool {

    private final String conversationId;
    private final ServerDefinition server;
    private final ServerConfigurationService configurationService;
    private final ServerOperationService operationService;
    private final SshCommandExecutor executor;
    private final ObjectMapper objectMapper;

    ServerCommandTool(String conversationId, ServerDefinition server,
                      ServerConfigurationService configurationService,
                      ServerOperationService operationService, SshCommandExecutor executor,
                      ObjectMapper objectMapper) {
        this.conversationId = conversationId;
        this.server = server;
        this.configurationService = configurationService;
        this.operationService = operationService;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "列出当前对话所选服务器允许执行的命令。只使用返回的命令 ID，不得编造命令")
    public String listCommands() {
        List<Map<String, String>> commands = configurationService.enabledCommands(server.id()).stream()
                .map(command -> Map.of(
                        "id", command.id(), "name", command.name(), "description", command.description(),
                        "riskLevel", command.riskLevel().name()))
                .toList();
        try { return objectMapper.writeValueAsString(commands); }
        catch (JsonProcessingException exception) { return "无法读取服务器命令清单"; }
    }

    @Tool(description = "执行当前服务器已配置的命令。普通命令立即执行；危险命令只生成确认选项，用户点击执行后才会运行")
    public String executeCommand(
            @ToolParam(description = "必须来自 listCommands 的命令 ID") String commandId,
            @ToolParam(description = "为什么需要执行，以及已有的判断依据") String reason
    ) {
        ServerCommandDefinition command = configurationService.requireEnabledCommand(server.id(), commandId);
        if (command.riskLevel() == ServerCommandRisk.DANGEROUS) {
            PendingServerOperationView pending = operationService.prepare(conversationId, server, command, reason);
            return "已生成危险命令“" + pending.commandName() + "”的确认选项，等待用户确认。"
                    + "不要声称命令已经执行，也不要输出内部操作编号。";
        }
        return executor.execute(server, command.commandText()).forModel();
    }
}
