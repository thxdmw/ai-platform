package com.thx.aiplatform.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConversationBindingServiceTest {

    @Test
    void 同一对话不能切换到其他服务器() {
        ServerConversationBindingService service = new ServerConversationBindingService();
        service.bind("conversation-1", "server-a");
        service.bind("conversation-1", "server-a");

        assertThatThrownBy(() -> service.bind("conversation-1", "server-b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("新建对话");
    }

    @Test
    void 删除绑定后同一对话编号可以重新绑定服务器() {
        ServerConversationBindingService service = new ServerConversationBindingService();
        service.bind("conversation-1", "server-a");

        service.remove("conversation-1");
        service.bind("conversation-1", "server-b");
    }
}
