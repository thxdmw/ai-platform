package com.thx.aiplatform.server.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证会话-服务器绑定的不可切换语义，以及删除绑定后同一会话可以重新绑定其他服务器。 */
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
