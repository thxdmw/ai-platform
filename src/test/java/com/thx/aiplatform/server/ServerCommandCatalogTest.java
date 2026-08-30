package com.thx.aiplatform.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerCommandCatalogTest {

    @Test
    void 服务名只能是单个安全标识符() {
        assertThat(ServerCommandCatalog.serviceStatus("nginx.service")).contains("nginx.service");
        assertThatThrownBy(() -> ServerCommandCatalog.serviceStatus("nginx; rm -rf /"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式不合法");
    }

    @Test
    void 日志行数会限制在安全范围() {
        assertThat(ServerCommandCatalog.serviceLogs("nginx", 9999)).endsWith("-n 500");
        assertThat(ServerCommandCatalog.containerLogs("app", 1)).contains("--tail 20");
    }
}
