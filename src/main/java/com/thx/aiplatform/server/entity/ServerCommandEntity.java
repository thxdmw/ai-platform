package com.thx.aiplatform.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.thx.aiplatform.server.enums.ServerCommandRisk;

/**
 * server_assistant_command 表的持久化实体，同时充当服务层内部流转的领域对象（网格中
 * 不再维护与实体重复的 ServerCommandDefinition 记录）。id 由服务层用 UUID 生成后
 * 手工赋值（INPUT）；created_at / updated_at 时间戳不进实体，始终由数据库维护
 * （同 {@link ServerEntity}）。
 */
@TableName("server_assistant_command")
public class ServerCommandEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String serverId;
    private String name;
    private String description;
    private String commandText;
    private String parameterSchema;
    private ServerCommandRisk riskLevel;
    private boolean enabled;
    private int sortOrder;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getServerId() { return serverId; }

    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getCommandText() { return commandText; }

    public void setCommandText(String commandText) { this.commandText = commandText; }

    public String getParameterSchema() { return parameterSchema; }

    public void setParameterSchema(String parameterSchema) { this.parameterSchema = parameterSchema; }

    public ServerCommandRisk getRiskLevel() { return riskLevel; }

    public void setRiskLevel(ServerCommandRisk riskLevel) { this.riskLevel = riskLevel; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSortOrder() { return sortOrder; }

    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /** 无参构造供 MyBatis-Plus 反射实例化（全参构造会覆盖默认无参构造，必须显式保留）。 */
    public ServerCommandEntity() { }

    /** 便捷全参构造（顺序对齐原 ServerCommandDefinition 记录），供服务层构建。 */
    public ServerCommandEntity(String id, String serverId, String name, String description, String commandText,
                               String parameterSchema, ServerCommandRisk riskLevel, boolean enabled, int sortOrder) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.description = description;
        this.commandText = commandText;
        this.parameterSchema = parameterSchema;
        this.riskLevel = riskLevel;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }
}