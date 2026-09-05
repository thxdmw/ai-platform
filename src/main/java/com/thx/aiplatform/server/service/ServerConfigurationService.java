package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.security.ServerCredentialCipher;
import com.thx.aiplatform.server.repository.ServerConfigurationRepository;
import com.thx.aiplatform.server.vo.ServerView;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.vo.ServerConnectionTestResult;
import com.thx.aiplatform.server.dto.ServerConfigurationRequest;
import com.thx.aiplatform.server.vo.ServerCommandView;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import com.thx.aiplatform.server.dto.ServerCommandRequest;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.model.ServerAuthenticationType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 服务器与命令配置的读写服务：CRUD、连接测试、默认命令安装，以及凭据安全的边界处理——
 * 凭据只以密文形态进出（加密在 {@link ServerCredentialCipher}），主机公钥入库前规范化
 * 校验。默认命令硬编码在代码里，为新服务器开箱即用，安装按名称判重，用户改过的同名命令
 * 不会被覆盖。
 *
 * <p>服务器/命令被修改或删除时，一律先取消该服务器的待确认操作：确认框里展示的内容
 * 是旧配置，允许继续执行会破坏确认语义。</p>
 */
@Service
public class ServerConfigurationService {

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
    private final ServerCommandTemplateService templateService;

    ServerConfigurationService(ServerConfigurationRepository repository, ServerRegistry registry,
                               ServerCredentialCipher credentialCipher, SshCommandExecutor executor,
                               ServerOperationService operationService,
                               ServerCommandTemplateService templateService) {
        this.repository = repository;
        this.registry = registry;
        this.credentialCipher = credentialCipher;
        this.executor = executor;
        this.operationService = operationService;
        this.templateService = templateService;
    }

    public List<ServerView> listServers() { return registry.views(); }

    /**
     * 新建服务器：凭据必填并加密入库（不允许存在「没有凭据的服务器」），随后自动补装
     * 默认只读诊断命令。
     */
    @Transactional
    public ServerView createServer(ServerConfigurationRequest request) {
        String credential = requiredCredential(request.credential());
        ServerAuthenticationType authenticationType = ServerAuthenticationType.parse(request.authenticationType());
        ServerEntity server = definition(UUID.randomUUID().toString(), request, authenticationType,
                credentialCipher.encrypt(credential), encryptedPassphrase(authenticationType, request.privateKeyPassphrase()));
        repository.insertServer(server);
        installMissingDefaultCommands(server.getId());
        return registry.toView(server);
    }

    /**
     * 更新服务器。凭据字段留空表示保留原密文（前端无需回传明文字段）；切换认证方式时
     * 强制要求重新填写凭据，否则会出现「认证方式已改、凭据还是旧形态」的悬空状态。
     * 更新前取消该服务器所有待确认操作——连接参数变化后，旧的危险命令确认选项必须作废。
     */
    @Transactional
    public ServerView updateServer(String id, ServerConfigurationRequest request) {
        ServerEntity existing = registry.require(id);
        ServerAuthenticationType authenticationType = ServerAuthenticationType.parse(request.authenticationType());
        boolean replacesCredential = request.credential() != null && !request.credential().isBlank();
        if (authenticationType != existing.getAuthenticationType() && !replacesCredential) {
            throw new IllegalArgumentException("切换认证方式时必须重新填写 SSH 密码或私钥");
        }
        String encryptedCredential = replacesCredential
                ? credentialCipher.encrypt(request.credential()) : existing.getCredentialCiphertext();
        String encryptedPassphrase = replacesCredential
                ? encryptedPassphrase(authenticationType, request.privateKeyPassphrase()) : existing.getPassphraseCiphertext();
        ServerEntity server = definition(id, request, authenticationType, encryptedCredential, encryptedPassphrase);
        operationService.cancelForServer(id);
        repository.updateServer(server);
        return registry.toView(server);
    }

    /**
     * 删除服务器前取消其全部待确认操作，防止删除后残留的确认框被点击时对一台已删除的
     * 服务器执行命令。
     */
    @Transactional
    public void deleteServer(String id) {
        operationService.cancelForServer(id);
        repository.deleteServer(id);
    }

    public List<ServerCommandView> listCommands(String serverId) {
        registry.require(serverId);
        return repository.findCommands(serverId, false).stream().map(this::toView).toList();
    }

    @Transactional
    public List<ServerCommandView> installDefaultCommands(String serverId) {
        registry.require(serverId);
        installMissingDefaultCommands(serverId);
        return repository.findCommands(serverId, false).stream().map(this::toView).toList();
    }

    /**
     * 新增命令：依靠库上的唯一约束兜底重名，把 DataIntegrityViolationException 翻译成
     * 用户可理解的业务错误，而不是让调用方看到数据库内部异常。
     */
    @Transactional
    public ServerCommandView createCommand(String serverId, ServerCommandRequest request) {
        registry.require(serverId);
        ServerCommandEntity command = commandDefinition(UUID.randomUUID().toString(), serverId, request);
        try { repository.insertCommand(command); }
        catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("同一服务器不能配置重名命令");
        }
        return toView(command);
    }

    /**
     * 更新命令前取消该服务器的待确认操作：命令文本一旦改变，页面确认框里展示的旧文本
     * 与实际执行的已经是两回事，确认语义被破坏，必须作废。
     */
    @Transactional
    public ServerCommandView updateCommand(String id, ServerCommandRequest request) {
        ServerCommandEntity existing = requireCommand(id);
        ServerCommandEntity command = commandDefinition(id, existing.getServerId(), request);
        operationService.cancelForServer(existing.getServerId());
        try { repository.updateCommand(command); }
        catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("同一服务器不能配置重名命令");
        }
        return toView(command);
    }

    @Transactional
    public void deleteCommand(String id) {
        ServerCommandEntity existing = requireCommand(id);
        operationService.cancelForServer(existing.getServerId());
        repository.deleteCommand(id);
    }

    public ServerConnectionTestResult testConnection(String id) {
        ServerEntity server = registry.requireEnabled(id);
        executor.testConnection(server);
        return new ServerConnectionTestResult(true, "SSH 连接和主机密钥校验成功");
    }

    public List<ServerCommandEntity> enabledCommands(String serverId) {
        return repository.findCommands(serverId, true);
    }

    /**
     * 供模型工具按命令 ID 取命令：同时校验命令属于当前对话的服务器且处于启用状态，
     * 防止工具拿 A 服务器的命令 ID 到 B 服务器执行。
     */
    public ServerCommandEntity requireEnabledCommand(String serverId, String commandId) {
        ServerCommandEntity command = requireCommand(commandId);
        if (!command.getServerId().equals(serverId)) throw new IllegalArgumentException("命令不属于当前对话选择的服务器");
        if (!command.isEnabled()) throw new IllegalArgumentException("命令已停用：" + command.getName());
        return command;
    }

    private ServerCommandEntity requireCommand(String id) {
        return repository.findCommand(id).orElseThrow(() -> new IllegalArgumentException("服务器命令不存在"));
    }

    /**
     * 按名称差量安装默认命令：名字已在库中（无论是否被用户修改过）就跳过，因此重复
     * 调用是幂等的，不会产生重名数据。
     */
    private void installMissingDefaultCommands(String serverId) {
        Set<String> existingNames = repository.findCommands(serverId, false).stream()
                .map(ServerCommandEntity::getName)
                .collect(Collectors.toSet());
        DEFAULT_COMMANDS.stream()
                .filter(template -> !existingNames.contains(template.name()))
                .map(template -> new ServerCommandEntity(UUID.randomUUID().toString(), serverId,
                        template.name(), template.description(), template.commandText(),
                        "[]", ServerCommandRisk.NORMAL, true, template.sortOrder()))
                .forEach(repository::insertCommand);
    }

    private ServerEntity definition(String id, ServerConfigurationRequest request,
                                    ServerAuthenticationType authenticationType,
                                    String encryptedCredential, String encryptedPassphrase) {
        return new ServerEntity(id, request.name().trim(), request.host().trim(),
                request.port() == null ? 22 : request.port(), request.username().trim(), authenticationType,
                encryptedCredential, encryptedPassphrase, normalizeHostKey(request.hostKey()),
                request.enabled() == null || request.enabled());
    }

    private ServerCommandEntity commandDefinition(String id, String serverId, ServerCommandRequest request) {
        String commandText = request.commandText().trim();
        if (commandText.indexOf('\0') >= 0) throw new IllegalArgumentException("命令内容不能包含空字符");
        String parameterSchema = templateService.normalizeSchema(commandText, request.parameterSchema());
        return new ServerCommandEntity(id, serverId, request.name().trim(), request.description().trim(),
                commandText, parameterSchema, ServerCommandRisk.parse(request.riskLevel()),
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

    /**
     * 主机公钥规范化：只取第一条完整的 known_hosts 记录（忽略注释与空白行），并要求
     * 「主机名/算法名/密钥体」三段齐全、密钥体足够长——防止把任意文本当公钥入库，
     * 否则 SSH 会始终连不上且错误晦涩难查。
     */
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

    private ServerCommandView toView(ServerCommandEntity command) {
        return new ServerCommandView(command.getId(), command.getServerId(), command.getName(), command.getDescription(),
                command.getCommandText(), command.getParameterSchema(), command.getRiskLevel().name(),
                command.isEnabled(), command.getSortOrder());
    }

    private record DefaultCommand(String name, String description, String commandText, int sortOrder) { }
}
