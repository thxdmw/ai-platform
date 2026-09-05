package com.thx.aiplatform.server.repository;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.entity.ServerCommandEntity;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 服务器与命令的 MyBatis-Plus 数据访问层，实体同时作为服务层内部流转的领域对象。
 * 实体无时间戳字段（见 {@link ServerEntity}），更新时间戳一律由
 * setSql("updated_at = CURRENT_TIMESTAMP") 交给数据库维护，与迁移前 SQL 语义一致；
 * 更新行数为 0 时抛「不存在」，并发下其他请求可能已删除该行，给调用方明确错误而不是
 * 静默成功。
 */
@Repository
public class ServerConfigurationRepository {

    private final ServerMapper serverMapper;
    private final ServerCommandMapper commandMapper;

    ServerConfigurationRepository(ServerMapper serverMapper, ServerCommandMapper commandMapper) {
        this.serverMapper = serverMapper;
        this.commandMapper = commandMapper;
    }

    public List<ServerEntity> findServers(boolean onlyEnabled) {
        return serverMapper.selectList(Wrappers.<ServerEntity>query()
                .eq(onlyEnabled, "enabled", true)
                .orderByAsc("created_at").orderByAsc("name"));
    }

    public Optional<ServerEntity> findServer(String id) {
        return Optional.ofNullable(serverMapper.selectById(id));
    }

    public void insertServer(ServerEntity server) {
        serverMapper.insert(server);
    }

    public void updateServer(ServerEntity server) {
        int updated = serverMapper.update(server, Wrappers.<ServerEntity>lambdaUpdate()
                .eq(ServerEntity::getId, server.getId())
                .setSql("updated_at = CURRENT_TIMESTAMP"));
        if (updated == 0) throw new IllegalArgumentException("服务器配置不存在");
    }

    public void deleteServer(String id) {
        if (serverMapper.deleteById(id) == 0) throw new IllegalArgumentException("服务器配置不存在");
    }

    public List<ServerCommandEntity> findCommands(String serverId, boolean onlyEnabled) {
        return commandMapper.selectList(Wrappers.<ServerCommandEntity>query()
                .eq("server_id", serverId)
                .eq(onlyEnabled, "enabled", true)
                .orderByAsc("sort_order").orderByAsc("created_at").orderByAsc("name"));
    }

    public Optional<ServerCommandEntity> findCommand(String id) {
        return Optional.ofNullable(commandMapper.selectById(id));
    }

    public void insertCommand(ServerCommandEntity command) {
        commandMapper.insert(command);
    }

    /**
     * 更新条件带上 server_id：防止并发下命令被挪到别的服务器后，按旧 id 误改新归属的行。
     */
    public void updateCommand(ServerCommandEntity command) {
        int updated = commandMapper.update(command, Wrappers.<ServerCommandEntity>lambdaUpdate()
                .eq(ServerCommandEntity::getId, command.getId())
                .eq(ServerCommandEntity::getServerId, command.getServerId())
                .setSql("updated_at = CURRENT_TIMESTAMP"));
        if (updated == 0) throw new IllegalArgumentException("服务器命令不存在");
    }

    public void deleteCommand(String id) {
        if (commandMapper.deleteById(id) == 0) throw new IllegalArgumentException("服务器命令不存在");
    }
}