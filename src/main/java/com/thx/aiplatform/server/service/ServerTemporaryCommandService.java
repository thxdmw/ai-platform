package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.model.PendingServerOperationView;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.model.ServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ReAct 临时命令入口。命令不需要预先保存：服务端能证明只读时立即执行，其他情况只创建
 * 待审批动作。工作目录与命令分开校验，避免模型通过拼接 cd 和 Shell 控制符绕过风险判断。
 */
@Service
public class ServerTemporaryCommandService {

    private static final Logger log = LoggerFactory.getLogger(ServerTemporaryCommandService.class);
    private final ServerCommandRiskClassifier riskClassifier;
    private final ServerOperationService operationService;
    private final SshCommandExecutor executor;

    ServerTemporaryCommandService(ServerCommandRiskClassifier riskClassifier,
                                  ServerOperationService operationService,
                                  SshCommandExecutor executor) {
        this.riskClassifier = riskClassifier;
        this.operationService = operationService;
        this.executor = executor;
    }

    /** 返回给模型的工具结果只包含执行事实或暂停状态，不泄露内部 actionId。 */
    public String executeOrPrepare(String conversationId, ServerDefinition server, String commandText,
                                   String workingDirectory, String reason) {
        String command = normalizeCommand(commandText);
        String directory = normalizeWorkingDirectory(workingDirectory);
        String renderedCommand = render(directory, command);
        ServerCommandRisk risk = riskClassifier.classify(command);
        boolean trustedExact = risk == ServerCommandRisk.DANGEROUS
                && operationService.isTrustedExact(conversationId, server.id(), directory, command);
        log.info("临时命令完成安全判定，conversationId={}，serverId={}，risk={}，trustedExact={}，workingDirectory={}",
                conversationId, server.id(), risk, trustedExact, directory == null ? "<default>" : directory);
        if (risk == ServerCommandRisk.NORMAL || trustedExact) {
            return executor.execute(server, renderedCommand).forModel();
        }
        PendingServerOperationView pending = operationService.prepareTemporary(
                conversationId, server, directory, command, renderedCommand, reason);
        return "任务已暂停：临时命令“" + pending.commandName() + "”需要用户选择后才能执行。"
                + "不要声称已经执行，不要改用其他方式绕过审批，也不要输出内部操作编号。";
    }

    static String normalizeCommand(String commandText) {
        if (commandText == null || commandText.isBlank()) throw new IllegalArgumentException("临时命令不能为空");
        String value = commandText.trim();
        if (value.length() > 2000) throw new IllegalArgumentException("临时命令不能超过 2000 个字符");
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("临时命令包含非法字符");
        return value;
    }

    static String normalizeWorkingDirectory(String workingDirectory) {
        if (workingDirectory == null || workingDirectory.isBlank()) return null;
        String value = workingDirectory.trim();
        if (!value.startsWith("/")) throw new IllegalArgumentException("工作目录必须是绝对路径");
        if (value.length() > 512 || value.indexOf('\0') >= 0 || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("工作目录不合法");
        }
        return value;
    }

    static String render(String workingDirectory, String command) {
        if (workingDirectory == null) return command;
        return "cd -- " + posixQuote(workingDirectory) + " && " + command;
    }

    private static String posixQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
