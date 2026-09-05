package com.thx.aiplatform.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * server_assistant_model_provider 表的持久化实体，同时充当服务层内部流转的领域对象
 * （网格中不再维护与实体重复的 ServerModelProviderDefinition 记录）。id 由服务层用
 * UUID 生成后手工赋值（INPUT）；created_at / updated_at 时间戳不进实体，始终由
 * 数据库维护（同 {@link ServerEntity}）。apiProtocol 是协议代码字符串
 * （'openai-completions' 等），不是 Java 枚举名。
 */
@TableName("server_assistant_model_provider")
public class ServerModelProviderEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String providerKey;
    private String name;
    private String baseUrl;
    private String chatCompletionsPath;
    private String apiProtocol;
    private String apiKeyCiphertext;
    private boolean enabled;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getProviderKey() { return providerKey; }

    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }

    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getChatCompletionsPath() { return chatCompletionsPath; }

    public void setChatCompletionsPath(String chatCompletionsPath) { this.chatCompletionsPath = chatCompletionsPath; }

    public String getApiProtocol() { return apiProtocol; }

    public void setApiProtocol(String apiProtocol) { this.apiProtocol = apiProtocol; }

    public String getApiKeyCiphertext() { return apiKeyCiphertext; }

    public void setApiKeyCiphertext(String apiKeyCiphertext) { this.apiKeyCiphertext = apiKeyCiphertext; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 无参构造供 MyBatis-Plus 反射实例化（全参构造会覆盖默认无参构造，必须显式保留）。 */
    public ServerModelProviderEntity() { }

    /** 便捷全参构造（顺序对齐原 ServerModelProviderDefinition 记录），供服务层构建。 */
    public ServerModelProviderEntity(String id, String providerKey, String name, String baseUrl,
                                     String chatCompletionsPath, String apiProtocol,
                                     String apiKeyCiphertext, boolean enabled) {
        this.id = id;
        this.providerKey = providerKey;
        this.name = name;
        this.baseUrl = baseUrl;
        this.chatCompletionsPath = chatCompletionsPath;
        this.apiProtocol = apiProtocol;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.enabled = enabled;
    }
}