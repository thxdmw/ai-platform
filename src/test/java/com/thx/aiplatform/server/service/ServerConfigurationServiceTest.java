package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.security.ServerCredentialCipher;
import com.thx.aiplatform.server.vo.ServerView;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.dto.ServerConfigurationRequest;
import com.thx.aiplatform.server.vo.ServerCommandView;
import com.thx.aiplatform.server.enums.ServerCommandRisk;
import com.thx.aiplatform.server.dto.ServerCommandRequest;
import com.thx.aiplatform.server.entity.ServerCommandEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ai-platform.server.credential-master-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Transactional
/** 集成测试：凭据只存密文且可解密还原、更新留空保留已有密文、默认命令补装幂等不重名。 */
class ServerConfigurationServiceTest {

    @Autowired ServerConfigurationService service;
    @Autowired ServerCredentialCipher credentialCipher;

    @Test
    void 服务器与命令分别持久化且凭据只保存密文() {
        ServerView server = service.createServer(new ServerConfigurationRequest(
                "服务器 A", "127.0.0.1", 2222, "ops", "PASSWORD", "very-secret", null,
                "[127.0.0.1]:2222 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestHostKeyMaterial", true));
        ServerCommandView command = service.createCommand(server.id(), new ServerCommandRequest(
                "查看运行时间", "查看服务器运行时间", "uptime", "[]", "NORMAL", true, 10));

        ServerEntity stored = service.findServer(server.id()).orElseThrow();
        assertThat(stored.getCredentialCiphertext()).doesNotContain("very-secret").startsWith("v1:");
        assertThat(new String(credentialCipher.decrypt(stored.getCredentialCiphertext()))).isEqualTo("very-secret");
        assertThat(service.findCommands(server.id(), false)).extracting(ServerCommandEntity::getId)
                .contains(command.id());
        assertThat(service.findCommands(server.id(), false)).extracting(ServerCommandEntity::getName)
                .contains("系统概览", "CPU 与内存", "磁盘使用", "高资源进程", "最近系统告警", "Docker 容器状态");
        assertThat(service.listServers()).singleElement().satisfies(view -> {
            assertThat(view.name()).isEqualTo("服务器 A");
            assertThat(view.credentialConfigured()).isTrue();
        });
    }

    @Test
    void 修改服务器时凭据留空会保留已有密文() {
        ServerView server = service.createServer(new ServerConfigurationRequest(
                "服务器 A", "127.0.0.1", 22, "ops", "PASSWORD", "original-secret", null,
                "127.0.0.1 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestHostKeyMaterial", true));
        String before = service.findServer(server.id()).orElseThrow().getCredentialCiphertext();

        service.updateServer(server.id(), new ServerConfigurationRequest(
                "服务器 A-修改", "127.0.0.1", 22, "ops", "PASSWORD", null, null,
                "127.0.0.1 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestHostKeyMaterial", true));

        assertThat(service.findServer(server.id()).orElseThrow().getCredentialCiphertext()).isEqualTo(before);
    }

    @Test
    void 常用只读命令可以重复补充且不会产生重名数据() {
        ServerView server = service.createServer(new ServerConfigurationRequest(
                "服务器 A", "127.0.0.1", 22, "ops", "PASSWORD", "secret", null,
                "127.0.0.1 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestHostKeyMaterial", true));

        service.installDefaultCommands(server.id());
        service.installDefaultCommands(server.id());

        assertThat(service.findCommands(server.id(), false))
                .hasSize(6)
                .allSatisfy(command -> {
                    assertThat(command.getRiskLevel()).isEqualTo(ServerCommandRisk.NORMAL);
                    assertThat(command.isEnabled()).isTrue();
                });
    }
}
