package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.dto.ServerCommandRequest;
import com.thx.aiplatform.server.dto.ServerConfigurationRequest;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.vo.ServerCommandView;
import com.thx.aiplatform.server.vo.ServerConnectionTestResult;
import com.thx.aiplatform.server.vo.ServerView;

import java.util.List;
import java.util.Optional;

/**
 * 服务器与命令配置域的唯一业务入口：CRUD、连接测试、默认命令安装、注册表查询
 * （require/requireEnabled 的统一「不存在即抛错、停用即拒绝」语义）与数据访问
 * （findServers/findServer/findCommands/findCommand，供本域内部与跨域查询使用）。
 */
public interface ServerConfigurationService {

    List<ServerView> listServers();

    ServerView createServer(ServerConfigurationRequest request);

    ServerView updateServer(String id, ServerConfigurationRequest request);

    void deleteServer(String id);

    List<ServerCommandView> listCommands(String serverId);

    List<ServerCommandView> installDefaultCommands(String serverId);

    ServerCommandView createCommand(String serverId, ServerCommandRequest request);

    ServerCommandView updateCommand(String id, ServerCommandRequest request);

    void deleteCommand(String id);

    ServerConnectionTestResult testConnection(String id);

    List<ServerCommandEntity> enabledCommands(String serverId);

    ServerCommandEntity requireEnabledCommand(String serverId, String commandId);

    /** 统一的「不存在即抛错」入口，所有按 ID 取服务器的调用都走这里，保证错误口径一致。 */
    ServerEntity require(String serverId);

    /** 要求服务器必须处于启用状态：停用的服务器不能被对话框选作执行目标。 */
    ServerEntity requireEnabled(String serverId);

    List<ServerEntity> findServers(boolean onlyEnabled);

    Optional<ServerEntity> findServer(String id);

    List<ServerCommandEntity> findCommands(String serverId, boolean onlyEnabled);

    Optional<ServerCommandEntity> findCommand(String id);
}