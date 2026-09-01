package com.thx.aiplatform.server.service;

import com.thx.aiplatform.platform.AssistantModelConnection;
import com.thx.aiplatform.server.model.ServerModelOptionRequest;
import com.thx.aiplatform.server.model.ServerModelProviderRequest;
import com.thx.aiplatform.server.model.ServerModelProviderView;
import com.thx.aiplatform.server.repository.ServerModelProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ai-platform.server.credential-master-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Transactional
class ServerModelProviderServiceTest {

    @Autowired ServerModelProviderService service;
    @Autowired ServerModelProviderRepository repository;

    @Test
    void 密钥只保存密文且所选模型能解析为兼容连接() {
        ServerModelProviderView provider = service.create(new ServerModelProviderRequest(
                "Coding 套餐", "https://coding.example.com/", "/v1/chat/completions", "sk-secret", true,
                List.of(new ServerModelOptionRequest("Coder", "coder-model", "high", true, 0))));

        assertThat(repository.findProvider(provider.id()).orElseThrow().apiKeyCiphertext())
                .startsWith("v1:").doesNotContain("sk-secret");
        assertThat(provider.apiKeyConfigured()).isTrue();
        assertThat(provider.toString()).doesNotContain("sk-secret");
        AssistantModelConnection connection = service.resolve(provider.models().getFirst().id());
        assertThat(connection.baseUrl()).isEqualTo("https://coding.example.com");
        assertThat(connection.chatCompletionsPath()).isEqualTo("/v1/chat/completions");
        assertThat(connection.model()).isEqualTo("coder-model");
        assertThat(connection.reasoningEffort()).isEqualTo("high");
        assertThat(connection.apiKey()).isEqualTo("sk-secret");
        assertThat(connection.toString()).doesNotContain("sk-secret");

        String modelId = provider.models().getFirst().id();
        ServerModelProviderView updated = service.update(provider.id(), new ServerModelProviderRequest(
                "Coding 套餐修改", "https://coding.example.com", "/v1/chat/completions", null, true,
                List.of(new ServerModelOptionRequest("Coder 新名称", "coder-model", "medium", true, 0))));
        assertThat(updated.models().getFirst().id()).isEqualTo(modelId);
    }
}
