package com.thx.aiplatform.server.controller;
import com.thx.aiplatform.server.service.ServerConfigurationService;
import com.thx.aiplatform.server.model.ServerView;
import com.thx.aiplatform.server.model.ServerConnectionTestResult;
import com.thx.aiplatform.server.model.ServerConfigurationRequest;
import com.thx.aiplatform.server.model.ServerCommandView;
import com.thx.aiplatform.server.model.ServerCommandRequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器与命令配置的 REST CRUD 端点，全部转发给 {@link ServerConfigurationService}。
 * 字段校验交给 @Valid，业务规则错误由模块级异常处理器统一转成 HTTP 状态。
 */
@RestController
@RequestMapping("/api/server/v1")
class ServerRegistryController {

    private final ServerConfigurationService configurationService;

    ServerRegistryController(ServerConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/servers")
    List<ServerView> listServers() { return configurationService.listServers(); }

    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    ServerView createServer(@Valid @RequestBody ServerConfigurationRequest request) {
        return configurationService.createServer(request);
    }

    @PutMapping("/servers/{serverId}")
    ServerView updateServer(@PathVariable String serverId,
                            @Valid @RequestBody ServerConfigurationRequest request) {
        return configurationService.updateServer(serverId, request);
    }

    @DeleteMapping("/servers/{serverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteServer(@PathVariable String serverId) { configurationService.deleteServer(serverId); }

    @PostMapping("/servers/{serverId}/test")
    ServerConnectionTestResult testConnection(@PathVariable String serverId) {
        return configurationService.testConnection(serverId);
    }

    @GetMapping("/servers/{serverId}/commands")
    List<ServerCommandView> listCommands(@PathVariable String serverId) {
        return configurationService.listCommands(serverId);
    }

    @PostMapping("/servers/{serverId}/commands")
    @ResponseStatus(HttpStatus.CREATED)
    ServerCommandView createCommand(@PathVariable String serverId,
                                    @Valid @RequestBody ServerCommandRequest request) {
        return configurationService.createCommand(serverId, request);
    }

    @PostMapping("/servers/{serverId}/commands/defaults")
    List<ServerCommandView> installDefaultCommands(@PathVariable String serverId) {
        return configurationService.installDefaultCommands(serverId);
    }

    @PutMapping("/commands/{commandId}")
    ServerCommandView updateCommand(@PathVariable String commandId,
                                    @Valid @RequestBody ServerCommandRequest request) {
        return configurationService.updateCommand(commandId, request);
    }

    @DeleteMapping("/commands/{commandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCommand(@PathVariable String commandId) { configurationService.deleteCommand(commandId); }
}
