package com.thx.aiplatform.server.tool;
import com.thx.aiplatform.server.service.SshCommandExecutor;
import com.thx.aiplatform.server.service.ServerOperationService;
import com.thx.aiplatform.server.service.ServerConfigurationService;
import com.thx.aiplatform.server.service.ServerCommandProposalService;
import com.thx.aiplatform.server.service.ServerCommandTemplateService;
import com.thx.aiplatform.server.service.ServerTemporaryCommandService;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.vo.PendingServerOperationView;
import com.thx.aiplatform.server.vo.PendingServerCommandProposalView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 暴露给模型的服务器命令工具集：listCommands / executeCommand / proposeCommand。
 *
 * <p>三个工具都只操作「已保存命令的命令 ID」或「服务端生成的确认选项」，模型永远无法
 * 拼接任意 Shell；危险命令一律走「生成确认选项 → 用户点击 → 服务端执行」流程，工具
 * 本身不直接执行危险命令。每个对话 new 一个实例，把会话与服务器绑进工具参数，避免
 * 单例工具在多会话之间串状态。</p>
 */
public final class ServerCommandTool {

    private static final Logger log = LoggerFactory.getLogger(ServerCommandTool.class);

    private final String conversationId;
    private final ServerEntity server;
    private final ServerConfigurationService configurationService;
    private final ServerOperationService operationService;
    private final ServerCommandProposalService commandProposalService;
    private final SshCommandExecutor executor;
    private final ObjectMapper objectMapper;
    private final ServerCommandTemplateService templateService;
    private final ServerTemporaryCommandService temporaryCommandService;

    public ServerCommandTool(String conversationId, ServerEntity server,
                      ServerConfigurationService configurationService,
                      ServerOperationService operationService, ServerCommandProposalService commandProposalService,
                      SshCommandExecutor executor,
                      ObjectMapper objectMapper,
                      ServerCommandTemplateService templateService,
                      ServerTemporaryCommandService temporaryCommandService) {
        this.conversationId = conversationId;
        this.server = server;
        this.configurationService = configurationService;
        this.operationService = operationService;
        this.commandProposalService = commandProposalService;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.templateService = templateService;
        this.temporaryCommandService = temporaryCommandService;
    }

    /**
     * 无需保存命名的临时命令入口。风险是否只读完全由服务端判定，模型提供的理由不参与
     * 放行；无法证明只读时工具只会暂停任务并生成审批卡片。
     */
    @Tool(description = "为当前任务运行临时 Shell 命令，无需先添加到命令列表。服务端确认只读时自动执行，否则暂停并等待用户选择；不得把危险命令拆分成多个只读命令绕过审批")
    public String executeTemporaryCommand(
            @ToolParam(description = "要执行的完整 Shell 命令。保持最小化、可复核，不要自行添加 cd；管道和重定向必须写在这里") String commandText,
            @ToolParam(description = "可选工作目录，必须是绝对路径；不需要指定时传空字符串") String workingDirectory,
            @ToolParam(description = "本步骤要验证或改变什么，以及为何需要执行") String reason
    ) {
        log.info("模型调用服务器工具，tool=executeTemporaryCommand，conversationId={}，serverId={}",
                conversationId, server.getId());
        commandProposalService.cancelForConversation(conversationId);
        return temporaryCommandService.executeOrPrepare(
                conversationId, server, commandText, workingDirectory, reason);
    }

    /**
     * 只返回命令的 ID/名称/描述/风险，刻意不返回命令文本：防止模型把完整命令抄进回答
     * 或试图改造成新命令；返回值里附带「无匹配命令时必须调用 proposeCommand」的提示，
     * 把工具契约写进模型可见的输出。
     */
    @Tool(description = "列出当前对话所选服务器允许执行的命令。只使用返回的命令 ID；没有匹配命令时必须继续调用 proposeCommand，不能只用文字询问是否添加")
    public String listCommands() {
        log.info("模型调用服务器工具，tool=listCommands，conversationId={}，serverId={}", conversationId, server.getId());
        List<Map<String, Object>> commands = configurationService.enabledCommands(server.getId()).stream()
                .map(command -> Map.<String, Object>of(
                        "id", command.getId(), "name", command.getName(), "description", command.getDescription(),
                        "riskLevel", command.getRiskLevel().name(),
                        "parameters", templateService.parameters(command)))
                .toList();
        Map<String, Object> result = Map.of(
                "commands", commands,
                "whenNoCommandMatches", "必须立即调用 proposeCommand 生成页面添加确认选项；不要在普通回答中自行列出命令或询问确认"
        );
        try { return objectMapper.writeValueAsString(result); }
        catch (JsonProcessingException exception) { return "无法读取服务器命令清单"; }
    }

    /**
     * 按 ID 执行已保存命令。危险命令不执行，只生成确认选项并要求模型不得声称已执行——
     * 模型有幻觉「命令已经跑完」的倾向，必须用返回值把边界钉死；普通命令直接执行并把
     * 结构化结果（退出码/输出/截断标记）交回模型。
     */
    @Tool(description = "执行当前服务器已配置的命令。普通命令立即执行；危险命令只生成确认选项，用户点击执行后才会运行")
    public String executeCommand(
            @ToolParam(description = "必须来自 listCommands 的命令 ID") String commandId,
            @ToolParam(description = "参数 JSON 对象；无参数命令传 {}。键和值必须符合 listCommands 返回的 parameters") String argumentsJson,
            @ToolParam(description = "为什么需要执行，以及已有的判断依据") String reason
    ) {
        log.info("模型调用服务器工具，tool=executeCommand，conversationId={}，serverId={}，commandId={}",
                conversationId, server.getId(), commandId);
        ServerCommandEntity command = configurationService.requireEnabledCommand(server.getId(), commandId);
        String renderedCommand = templateService.render(command, argumentsJson);
        if (command.getRiskLevel() == ServerCommandRisk.DANGEROUS) {
            commandProposalService.cancelForConversation(conversationId);
            PendingServerOperationView pending = operationService.prepare(
                    conversationId, server, command, renderedCommand, reason);
            return "已生成危险命令“" + pending.commandName() + "”的确认选项，等待用户确认。"
                    + "不要声称命令已经执行，也不要输出内部操作编号。";
        }
        String result = executor.execute(server, renderedCommand).forModel();
        log.info("服务器工具执行完成，conversationId={}，serverId={}，commandId={}",
                conversationId, server.getId(), commandId);
        return result;
    }

    /**
     * 提出命令添加提议：命令文本、风险分级都由服务端校验与判定，模型只提供名称、描述
     * 与理由；同样要求模型不得声称已保存——保存必须以用户点击页面确认按钮为前提。
     */
    @Tool(description = "当前服务器缺少完成用户请求所需的命令时，提出一条固定命令并生成添加确认选项。风险等级由服务端自动判定，未经用户确认不会保存或执行")
    public String proposeCommand(
            @ToolParam(description = "便于用户识别的简短命令名称，不超过 80 个字符") String name,
            @ToolParam(description = "命令用途、预期结果和使用场景，不超过 500 个字符") String description,
            @ToolParam(description = "固定 Shell 模板。可变参数只能以独占参数形式写成 {{name}}，不能拼接到其他字符中") String commandText,
            @ToolParam(description = "参数规则 JSON 数组；无参数传 []。类型支持 PATH/ENUM/INTEGER/TEXT；PATH 必须提供 allowedRoots，ENUM 必须提供 allowedValues") String parameterSchema,
            @ToolParam(description = "为什么当前任务需要添加这条命令") String reason
    ) {
        log.info("模型调用服务器工具，tool=proposeCommand，conversationId={}，serverId={}，name={}",
                conversationId, server.getId(), name);
        operationService.cancelForConversation(conversationId);
        PendingServerCommandProposalView pending = commandProposalService.prepare(
                conversationId, server, name, description, commandText, parameterSchema, reason);
        return "已生成命令“" + pending.commandName() + "”的添加确认选项，服务端判定风险等级为 "
                + pending.riskLevel() + "。等待用户确认，不要声称命令已经保存或执行，也不要输出内部操作编号。";
    }
}
