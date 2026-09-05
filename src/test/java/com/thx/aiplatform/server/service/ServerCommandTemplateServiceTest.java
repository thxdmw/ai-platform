package com.thx.aiplatform.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.model.ServerCommandRisk;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerCommandTemplateServiceTest {

    private final ServerCommandTemplateService service = new ServerCommandTemplateService(new ObjectMapper());

    @Test
    void 路径参数可以复用且执行前统一转义() {
        String schema = service.normalizeSchema("find {{path}} -maxdepth {{depth}} -type f", """
                [
                  {"name":"path","type":"PATH","allowedRoots":["/var/log","/srv/apps"]},
                  {"name":"depth","type":"INTEGER","minValue":1,"maxValue":5}
                ]
                """);
        ServerCommandEntity command = command("find {{path}} -maxdepth {{depth}} -type f", schema);

        assertThat(service.render(command, "{\"path\":\"/var/log/nginx\",\"depth\":\"2\"}"))
                .isEqualTo("find '/var/log/nginx' -maxdepth '2' -type f");
    }

    @Test
    void 路径越界与缺少参数都会拒绝() {
        String schema = service.normalizeSchema("ls -la {{path}}", """
                [{"name":"path","type":"PATH","allowedRoots":["/var/log"]}]
                """);
        ServerCommandEntity command = command("ls -la {{path}}", schema);

        assertThatThrownBy(() -> service.render(command, "{\"path\":\"/etc\"}"))
                .hasMessageContaining("超出允许目录");
        assertThatThrownBy(() -> service.render(command, "{}"))
                .hasMessageContaining("缺少");
    }

    @Test
    void 文本中的Shell字符只会成为单个参数() {
        String schema = service.normalizeSchema("journalctl -u {{service}}", """
                [{"name":"service","type":"TEXT","pattern":"[A-Za-z0-9_.; -]+","maxLength":80}]
                """);
        ServerCommandEntity command = command("journalctl -u {{service}}", schema);

        assertThat(service.render(command, "{\"service\":\"nginx; reboot\"}"))
                .isEqualTo("journalctl -u 'nginx; reboot'");
    }

    private ServerCommandEntity command(String template, String schema) {
        return new ServerCommandEntity("command-1", "server-1", "模板", "测试", template, schema,
                ServerCommandRisk.NORMAL, true, 0);
    }
}
