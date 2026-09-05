package com.thx.aiplatform.server.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.thx.aiplatform.server.model.ServerAuthenticationType;

/**
 * server_assistant_server 表的持久化实体，同时充当服务层内部流转的领域对象（网格中
 * 不再维护与实体重复的 ServerDefinition 记录）。id 由服务层用 UUID 生成后手工赋值
 * （INPUT）；created_at / updated_at 时间戳刻意不进实体——始终由数据库默认值/
 * CURRENT_TIMESTAMP 维护，避免读取 TIMESTAMP WITH TIME ZONE 列在 H2 与 PostgreSQL
 * 之间映射不一致。
 *
 * <p>passphraseCiphertext 可为空，且「清空口令」必须能把列写回 NULL，因此更新策略
 * 用 ALWAYS：updateById/update 时即使字段为 null 也会生成 SET 子句，而不是被默认的
 * NOT_NULL 策略跳过。</p>
 */
@TableName("server_assistant_server")
public class ServerEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String host;
    private int port;
    private String username;
    private ServerAuthenticationType authenticationType;
    private String credentialCiphertext;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String passphraseCiphertext;
    private String hostKey;
    private boolean enabled;

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }

    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }

    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public ServerAuthenticationType getAuthenticationType() { return authenticationType; }

    public void setAuthenticationType(ServerAuthenticationType authenticationType) { this.authenticationType = authenticationType; }

    public String getCredentialCiphertext() { return credentialCiphertext; }

    public void setCredentialCiphertext(String credentialCiphertext) { this.credentialCiphertext = credentialCiphertext; }

    public String getPassphraseCiphertext() { return passphraseCiphertext; }

    public void setPassphraseCiphertext(String passphraseCiphertext) { this.passphraseCiphertext = passphraseCiphertext; }

    public String getHostKey() { return hostKey; }

    public void setHostKey(String hostKey) { this.hostKey = hostKey; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 无参构造供 MyBatis-Plus 反射实例化（全参构造会覆盖默认无参构造，必须显式保留）。 */
    public ServerEntity() { }

    /** 便捷全参构造（顺序对齐原 ServerDefinition 记录），供服务层构建。 */
    public ServerEntity(String id, String name, String host, int port, String username,
                        ServerAuthenticationType authenticationType, String credentialCiphertext,
                        String passphraseCiphertext, String hostKey, boolean enabled) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.authenticationType = authenticationType;
        this.credentialCiphertext = credentialCiphertext;
        this.passphraseCiphertext = passphraseCiphertext;
        this.hostKey = hostKey;
        this.enabled = enabled;
    }
}