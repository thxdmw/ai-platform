package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.config.ServerAssistantProperties;
import com.thx.aiplatform.server.service.impl.ServerActionContinuationServiceImpl;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证续跑凭证的会话/服务器绑定与一次性消费语义，错绑与重复消费都被拒绝。 */
class ServerActionContinuationServiceTest {

    @Test
    void 续跑凭证绑定会话和服务器且只能消费一次() {
        ServerAssistantProperties properties = new ServerAssistantProperties();
        properties.setApprovalTtl(Duration.ofMinutes(10));
        ServerActionContinuationService service = new ServerActionContinuationServiceImpl(properties,
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
        String id = service.prepare("conversation-1", "server-a", "继续原任务");

        assertThatThrownBy(() -> service.consume(id, "conversation-2", "server-a"))
                .hasMessageContaining("不匹配");

        assertThat(service.consume(id, "conversation-1", "server-a")).isEqualTo("继续原任务");
        assertThatThrownBy(() -> service.consume(id, "conversation-1", "server-a"))
                .hasMessageContaining("不存在或已使用");
    }
}
