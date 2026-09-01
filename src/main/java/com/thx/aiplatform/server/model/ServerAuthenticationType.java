package com.thx.aiplatform.server.model;

/**
 * 服务器认证方式枚举。parse 对大小写与首尾空格容错（PASSWORD/password 均可），未知值
 * 抛业务异常而非底层枚举转换异常，避免错误信息里出现技术细节。
 */
public enum ServerAuthenticationType {
    PASSWORD,
    PRIVATE_KEY;

    public static ServerAuthenticationType parse(String value) {
        try { return valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("认证方式仅支持 PASSWORD 或 PRIVATE_KEY");
        }
    }
}
