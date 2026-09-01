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
    private final ServerCommandProposalService commandProposalService;
    private final SshCommandExecutor executor;
    private final ObjectMapper objectMapper;

    ServerCommandTool(String conversationId, ServerDefinition server,
                      ServerConfigurationService configurationService,
                      ServerOperationService operationService, ServerCommandProposalService commandProposalService,
                      SshCommandExecutor executor,
                      ObjectMapper objectMapper) {
        this.conversationId = conversationId;
        this.server = server;
        this.configurationService = configurationService;
        this.operationService = operationService;
        this.commandProposalService = commandProposalService;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "列出当前对话所选服务器允许执行的命令。只使用返回的命令 ID；没有匹配命令时必须继续调用 proposeCommand，不能只用文字询问是否添加")
    public String listCommands() {
        List<Map<String, String>> commands = configurationService.enabledCommands(server.id()).stream()
                .map(command -> Map.of(
                        "id", command.id(), "name", command.name(), "description", command.description(),
                        "riskLevel", command.riskLevel().name()))
                .toList();
        Map<String, Object> result = Map.of(
                "commands", commands,
                "whenNoCommandMatches", "必须立即调用 proposeCommand 生成页面添加确认选项；不要在普通回答中自行列出命令或询问确认"
        );
        try { return objectMapper.writeValueAsString(result); }
        catch (JsonProcessingException exception) { return "无法读取服务器命令清单"; }
    }

    @Tool(description = "执行当前服务器已配置的命令。普通命令立即执行；危险命令只生成确认选项，用户点击执行后才会运行")
    public String executeCommand(
            @ToolParam(description = "必须来自 listCommands 的命令 ID") String commandId,
            @ToolParam(description = "为什么需要执行，以及已有的判断依据") String reason
    ) {
        ServerCommandDefinition command = configurationService.requireEnabledCommand(server.id(), commandId);
        if (command.riskLevel() == ServerCommandRisk.DANGEROUS) {
            commandProposalService.cancelForConversation(conversationId);
            PendingServerOperationView pending = operationService.prepare(conversationId, server, command, reason);
            return "已生成危险命令“" + pending.commandName() + "”的确认选项，等待用户确认。"
                    + "不要声称命令已经执行，也不要输出内部操作编号。";
        }
        return executor.execute(server, command.commandText()).forModel();
    }

    @Tool(description = "当前服务器缺少完成用户请求所需的命令时，提出一条固定命令并生成添加确认选项。风险等级由服务端自动判定，未经用户确认不会保存或执行")
    public String proposeCommand(
            @ToolParam(description = "便于用户识别的简短命令名称，不超过 80 个字符") String name,
            @ToolParam(description = "命令用途、预期结果和使用场景，不超过 500 个字符") String description,
            @ToolParam(description = "待添加的完整固定 Shell 命令，不得包含占位符或把用户输入直接拼入命令") String commandText,
            @ToolParam(description = "为什么当前任务需要添加这条命令") String reason
    ) {
        operationService.cancelForConversation(conversationId);
        PendingServerCommandProposalView pending = commandProposalService.prepare(
                conversationId, server, name, description, commandText, reason);
        return "已生成命令“" + pending.commandName() + "”的添加确认选项，服务端判定风险等级为 "
                + pending.riskLevel() + "。等待用户确认，不要声称命令已经保存或执行，也不要输出内部操作编号。";
    }
}
