package com.thx.aiplatform.server;

enum ServerAuthenticationType {
    PASSWORD,
    PRIVATE_KEY;

    static ServerAuthenticationType parse(String value) {
        try { return valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("认证方式仅支持 PASSWORD 或 PRIVATE_KEY");
        }
    }
}
