package com.thx.aiplatform.server;

enum ServerCommandRisk {
    NORMAL,
    DANGEROUS;

    static ServerCommandRisk parse(String value) {
        try { return valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("风险等级仅支持 NORMAL 或 DANGEROUS");
        }
    }
}
