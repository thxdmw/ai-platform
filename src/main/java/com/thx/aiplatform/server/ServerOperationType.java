package com.thx.aiplatform.server;

enum ServerOperationType {
    RESTART_SERVICE,
    RESTART_CONTAINER;

    static ServerOperationType parse(String value) {
        try { return valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("仅支持 RESTART_SERVICE 或 RESTART_CONTAINER");
        }
    }
}
