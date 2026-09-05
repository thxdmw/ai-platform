package com.thx.aiplatform.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * server_assistant_model 表的持久化实体，同时充当服务层内部流转的领域对象（网格中
 * 不再维护与实体重复的 ServerModelDefinition 记录）。id 由服务层用 UUID 生成后
 * 手工赋值（INPUT）；created_at / updated_at 时间戳不进实体，始终由数据库维护
 * （同 {@link ServerEntity}）。
 */
@TableName("server_assistant_model")
public class ServerModelEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String providerId;
    private String name;
    private String modelCode;
    private String reasoningEffort;
    private boolean enabled;
    private int sortOrder;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getProviderId() { return providerId; }

    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getModelCode() { return modelCode; }

    public void setModelCode(String modelCode) { this.modelCode = modelCode; }

    public String getReasoningEffort() { return reasoningEffort; }

    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getSortOrder() { return sortOrder; }

    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /** 无参构造供 MyBatis-Plus 反射实例化（全参构造会覆盖默认无参构造，必须显式保留）。 */
    public ServerModelEntity() { }

    /** 便捷全参构造（顺序对齐原 ServerModelDefinition 记录），供服务层构建。 */
    public ServerModelEntity(String id, String providerId, String name, String modelCode,
                             String reasoningEffort, boolean enabled, int sortOrder) {
        this.id = id;
        this.providerId = providerId;
        this.name = name;
        this.modelCode = modelCode;
        this.reasoningEffort = reasoningEffort;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }
}