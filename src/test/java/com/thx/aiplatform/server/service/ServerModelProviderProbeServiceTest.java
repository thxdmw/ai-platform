package com.thx.aiplatform.server.service;

import com.sun.net.httpserver.HttpServer;
import com.thx.aiplatform.server.dto.ServerModelProviderProbeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ai-platform.server.credential-master-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
class ServerModelProviderProbeServiceTest {

    @Autowired ServerModelProviderProbeService service;

    @Test
    void 测试连接会携带鉴权并读取模型目录() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":[{\"id\":\"model-b\"},{\"id\":\"model-a\",\"name\":\"模型 A\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var result = service.probe(new ServerModelProviderProbeRequest(null,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "openai-completions", "sk-test"));

            assertThat(result.success()).isTrue();
            assertThat(result.models()).extracting(model -> model.id()).containsExactly("model-a", "model-b");
            assertThat(result.models().getFirst().name()).isEqualTo("模型 A");
            assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        } finally {
            server.stop(0);
        }
    }
}
