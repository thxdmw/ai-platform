package com.thx.aiplatform.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
class ServerQueryTools {

    private static final Logger log = LoggerFactory.getLogger(ServerQueryTools.class);
    private final ServerRegistry registry;
    private final SshCommandExecutor executor;
    private final ObjectMapper objectMapper;

    ServerQueryTools(ServerRegistry registry, SshCommandExecutor executor, ObjectMapper objectMapper) {
        this.registry = registry;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "列出当前允许管理的服务器，以及每台服务器允许操作的服务和容器")
    public String listServers() {
        try { return objectMapper.writeValueAsString(registry.views()); }
        catch (JsonProcessingException exception) { return "无法读取服务器清单"; }
    }

    @Tool(description = "查看指定服务器的运行时间、内存、磁盘和系统版本")
    public String getSystemOverview(@ToolParam(description = "服务器 ID") String serverId) {
        return execute("系统概览", serverId, ServerCommandCatalog.overview());
    }

    @Tool(description = "查看指定服务器 CPU 占用最高的进程")
    public String getTopProcesses(@ToolParam(description = "服务器 ID") String serverId) {
        return execute("进程列表", serverId, ServerCommandCatalog.processes());
    }

    @Tool(description = "查看指定服务器正在运行的 Docker 容器")
    public String getDockerStatus(@ToolParam(description = "服务器 ID") String serverId) {
        return execute("Docker 状态", serverId, ServerCommandCatalog.dockerStatus());
    }

    @Tool(description = "查看白名单内 systemd 服务的状态")
    public String getServiceStatus(@ToolParam(description = "服务器 ID") String serverId,
                                   @ToolParam(description = "服务名") String service) {
        ServerDefinition server = registry.require(serverId);
        requireAllowed(server.allowsService(service), "服务", service);
        return execute("服务状态", serverId, ServerCommandCatalog.serviceStatus(service));
    }

    @Tool(description = "读取白名单内 systemd 服务的最近日志，最多 500 行")
    public String getServiceLogs(@ToolParam(description = "服务器 ID") String serverId,
                                 @ToolParam(description = "服务名") String service,
                                 @ToolParam(description = "日志行数", required = false) Integer lines) {
        ServerDefinition server = registry.require(serverId);
        requireAllowed(server.allowsService(service), "服务", service);
        return execute("服务日志", serverId, ServerCommandCatalog.serviceLogs(service, lines == null ? 100 : lines));
    }

    @Tool(description = "读取白名单内 Docker 容器的最近日志，最多 500 行")
    public String getContainerLogs(@ToolParam(description = "服务器 ID") String serverId,
                                   @ToolParam(description = "容器名") String container,
                                   @ToolParam(description = "日志行数", required = false) Integer lines) {
        ServerDefinition server = registry.require(serverId);
        requireAllowed(server.allowsContainer(container), "容器", container);
        return execute("容器日志", serverId, ServerCommandCatalog.containerLogs(container, lines == null ? 100 : lines));
    }

    private String execute(String operation, String serverId, String command) {
        try { return executor.execute(registry.require(serverId), command).forModel(); }
        catch (RuntimeException exception) {
            log.warn("服务器只读工具失败，operation={}，serverId={}，reason={}", operation, serverId, exception.getMessage());
            return operation + "失败：" + exception.getMessage();
        }
    }

    private void requireAllowed(boolean allowed, String type, String target) {
        if (!allowed) throw new IllegalArgumentException(type + "不在当前服务器白名单中：" + target);
    }
}
