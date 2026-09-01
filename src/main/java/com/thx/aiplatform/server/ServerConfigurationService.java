package com.thx.aiplatform.server;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class ServerConfigurationService {

    private static final List<DefaultCommand> DEFAULT_COMMANDS = List.of(
            new DefaultCommand("系统概览", "查看主机、内核、运行时间和当前负载",
                    "printf '%s\\n' '=== 主机 ==='; hostname; uname -srmo; printf '%s\\n' '=== 运行时间与负载 ==='; uptime", 10),
            new DefaultCommand("CPU 与内存", "查看 CPU 核心数和内存使用情况",
                    "printf '%s\\n' '=== CPU 核心数 ==='; nproc; printf '%s\\n' '=== 内存 ==='; free -h", 20),
            new DefaultCommand("磁盘使用", "查看本地文件系统容量和使用率",
                    "df -hT -x tmpfs -x devtmpfs", 30),
            new DefaultCommand("高资源进程", "查看 CPU 使用率最高的进程",
                    "ps -eo pid,user,%cpu,%mem,etime,comm --sort=-%cpu | head -n 16", 40),
            new DefaultCommand("最近系统告警", "查看 systemd 日志中的最近告警",
                    "journalctl -p warning -n 80 --no-pager", 50),
            new DefaultCommand("Docker 容器状态", "查看正在运行的 Docker 容器",
                    "docker ps --format 'table {{.Names}}\\t{{.Image}}\\t{{.Status}}\\t{{.Ports}}'", 60)
    );

    private final ServerConfigurationRepository repository;
    private final ServerRegistry registry;
    private final ServerCredentialCipher credentialCipher;
    private final SshCommandExecutor executor;
    private final ServerOperationService operationService;

    ServerConfigurationService(ServerConfigurationRepository repository, ServerRegistry registry,
                               ServerCredentialCipher credentialCipher, SshCommandExecutor executor,
                               ServerOperationService operationService) {
        this.repository = repository;
        this.registry = registry;
        this.credentialCipher = credentialCipher;
        this.executor = executor;
        this.operationService = operationService;
    }

    List<ServerView> listServers() { return registry.views(); }

    @Transactional
    ServerView createServer(ServerConfigurationRequest request) {
        String credential = requiredCredential(request.credential());
        ServerAuthenticationType authenticationType = ServerAuthenticationType.parse(request.authenticationType());
        ServerDefinition server = definition(UUID.randomUUID().toString(), request, authenticationType,
                credentialCipher.encrypt(credential), encryptedPassphrase(authenticationType, request.privateKeyPassphrase()));
        repository.insertServer(server);
        installMissingDefaultCommands(server.id());
        return registry.toView(server);
    }

    @Transactional
    ServerView updateServer(String id, ServerConfigurationRequest request) {
        ServerDefinition existing = registry.require(id);
        ServerAuthenticationType authenticationType = ServerAuthenticationType.parse(request.authenticationType());
        boolean replacesCredential = request.credential() != null && !request.credential().isBlank();
        if (authenticationType != existing.authenticationType() && !replacesCredential) {
            throw new IllegalArgumentException("切换认证方式时必须重新填写 SSH 密码或私钥");
        }
        String encryptedCredential = replacesCredential
                ? credentialCipher.encrypt(request.credential()) : existing.credentialCiphertext();
        String encryptedPassphrase = replacesCredential
                ? encryptedPassphrase(authenticationType, request.privateKeyPassphrase()) : existing.passphraseCiphertext();
        ServerDefinition server = definition(id, request, authenticationType, encryptedCredential, encryptedPassphrase);
        operationService.cancelForServer(id);
        repository.updateServer(server);
        return registry.toView(server);
    }

    @Transactional
    void deleteServer(String id) {
        operationService.cancelForServer(id);
        repository.deleteServer(id);
    }

    List<ServerCommandView> listCommands(String serverId) {
        registry.require(serverId);
        return repository.findCommands(serverId, false).stream().map(this::toView).toList();
    }

    @Transactional
    List<ServerCommandView> installDefaultCommands(String serverId) {
        registry.require(serverId);
        installMissingDefaultCommands(serverId);
        return repository.findCommands(serverId, false).stream().map(this::toView).toList();
    }

    @Transactional
    ServerCommandView createCommand(String serverId, ServerCommandRequest request) {
        registry.require(serverId);
        ServerCommandDefinition command = commandDefinition(UUID.randomUUID().toString(), serverId, request);
        try { repository.insertCommand(command); }
        catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("同一服务器不能配置重名命令");
        }
        return toView(command);
    }

    @Transactional
    ServerCommandView updateCommand(String id, ServerCommandRequest request) {
        ServerCommandDefinition existing = requireCommand(id);
        ServerCommandDefinition command = commandDefinition(id, existing.serverId(), request);
        operationService.cancelForServer(existing.serverId());
        try { repository.updateCommand(command); }
        catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("同一服务器不能配置重名命令");
        }
        return toView(command);
    }

    @Transactional
    void deleteCommand(String id) {
        ServerCommandDefinition existing = requireCommand(id);
        operationService.cancelForServer(existing.serverId());
        repository.deleteCommand(id);
    }

    ServerConnectionTestResult testConnection(String id) {
        ServerDefinition server = registry.requireEnabled(id);
        executor.testConnection(server);
        return new ServerConnectionTestResult(true, "SSH 连接和主机密钥校验成功");
    }

    List<ServerCommandDefinition> enabledCommands(String serverId) {
        return repository.findCommands(serverId, true);
    }

    ServerCommandDefinition requireEnabledCommand(String serverId, String commandId) {
        ServerCommandDefinition command = requireCommand(commandId);
        if (!command.serverId().equals(serverId)) throw new IllegalArgumentException("命令不属于当前对话选择的服务器");
        if (!command.enabled()) throw new IllegalArgumentException("命令已停用：" + command.name());
        return command;
    }

    private ServerCommandDefinition requireCommand(String id) {
        return repository.findCommand(id).orElseThrow(() -> new IllegalArgumentException("服务器命令不存在"));
    }

    private void installMissingDefaultCommands(String serverId) {
        Set<String> existingNames = repository.findCommands(serverId, false).stream()
                .map(ServerCommandDefinition::name)
                .collect(Collectors.toSet());
        DEFAULT_COMMANDS.stream()
                .filter(template -> !existingNames.contains(template.name()))
                .map(template -> new ServerCommandDefinition(UUID.randomUUID().toString(), serverId,
                        template.name(), template.description(), template.commandText(),
                        ServerCommandRisk.NORMAL, true, template.sortOrder()))
                .forEach(repository::insertCommand);
    }

    private ServerDefinition definition(String id, ServerConfigurationRequest request,
                                        ServerAuthenticationType authenticationType,
                                        String encryptedCredential, String encryptedPassphrase) {
        return new ServerDefinition(id, request.name().trim(), request.host().trim(),
                request.port() == null ? 22 : request.port(), request.username().trim(), authenticationType,
                encryptedCredential, encryptedPassphrase, normalizeHostKey(request.hostKey()),
                request.enabled() == null || request.enabled());
    }

    private ServerCommandDefinition commandDefinition(String id, String serverId, ServerCommandRequest request) {
        String commandText = request.commandText().trim();
        if (commandText.indexOf('\0') >= 0) throw new IllegalArgumentException("命令内容不能包含空字符");
        return new ServerCommandDefinition(id, serverId, request.name().trim(), request.description().trim(),
                commandText, ServerCommandRisk.parse(request.riskLevel()),
                request.enabled() == null || request.enabled(), request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private String requiredCredential(String credential) {
        if (credential == null || credential.isBlank()) throw new IllegalArgumentException("SSH 密码或私钥不能为空");
        return credential;
    }

    private String encryptedPassphrase(ServerAuthenticationType type, String passphrase) {
        if (type != ServerAuthenticationType.PRIVATE_KEY || passphrase == null || passphrase.isEmpty()) return null;
        return credentialCipher.encrypt(passphrase);
    }

    private String normalizeHostKey(String value) {
        String line = value.lines().map(String::trim)
                .filter(item -> !item.isEmpty() && !item.startsWith("#"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("SSH 主机公钥不能为空"));
        String[] fields = line.split("\\s+");
        if (fields.length < 3 || !fields[1].matches("[A-Za-z0-9@._+-]+") || fields[2].length() < 20) {
            throw new IllegalArgumentException("SSH 主机公钥必须是 ssh-keyscan 输出的完整记录");
        }
        return line;
    }

    private ServerCommandView toView(ServerCommandDefinition command) {
        return new ServerCommandView(command.id(), command.serverId(), command.name(), command.description(),
                command.commandText(), command.riskLevel().name(), command.enabled(), command.sortOrder());
    }

    private record DefaultCommand(String name, String description, String commandText, int sortOrder) { }
}
