package com.thx.aiplatform.server.model;

/**
 * 命令风险等级：NORMAL 可直接执行，DANGEROUS 必须先生成确认选项、经用户点击后才执行。
 * parse 做 trim/大写容错，与 {@link ServerAuthenticationType#parse} 同一套容错约定。
 */
public enum ServerCommandRisk {
    NORMAL,
    DANGEROUS;

    public static ServerCommandRisk parse(String value) {
        try { return valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("风险等级仅支持 NORMAL 或 DANGEROUS");
        }
    }
}
