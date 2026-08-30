package com.thx.aiplatform.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerRegistryTest {

    @Test
    void 可以加载多台服务器且不会向页面暴露凭据配置() {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setServersJson("""
                [
                  {"id":"server-a","name":"服务器 A","host":"a.example.com","username":"ops",
                   "passwordEnv":"SERVER_A_PASSWORD","knownHostsPath":"/secrets/known_hosts",
                   "allowedServices":["nginx"],"allowedContainers":["app"]},
                  {"id":"server-b","name":"服务器 B","host":"10.0.0.2","port":2222,"username":"admin",
                   "privateKeyPath":"/secrets/id_ed25519","knownHostsPath":"/secrets/known_hosts"}
                ]
                """);

        ServerRegistry registry = new ServerRegistry(properties, new ObjectMapper());

        assertThat(registry.views()).hasSize(2);
        assertThat(registry.views()).filteredOn(server -> server.id().equals("server-a")).singleElement()
                .satisfies(server -> assertThat(server.allowedServices()).containsExactly("nginx"));
        assertThat(registry.require("server-b").port()).isEqualTo(2222);
    }

    @Test
    void 重复服务器编号会阻止启动() {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setServersJson("""
                [{"id":"same","host":"a","username":"u","passwordEnv":"P","knownHostsPath":"k"},
                 {"id":"same","host":"b","username":"u","passwordEnv":"P","knownHostsPath":"k"}]
                """);

        assertThatThrownBy(() -> new ServerRegistry(properties, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }
}
