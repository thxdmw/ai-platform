package com.thx.aiplatform.server;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
class ServerConversationBindingService {

    private static final int MAX_BINDINGS = 1000;
    private final Map<String, String> bindings = new LinkedHashMap<>(32, 0.75f, true);

    synchronized void bind(String conversationId, String serverId) {
        String existing = bindings.get(conversationId);
        if (existing != null && !existing.equals(serverId)) {
            throw new IllegalArgumentException("同一对话不能切换服务器，请新建对话后再选择其他服务器");
        }
        bindings.put(conversationId, serverId);
        while (bindings.size() > MAX_BINDINGS) {
            bindings.remove(bindings.keySet().iterator().next());
        }
    }

    synchronized void remove(String conversationId) {
        bindings.remove(conversationId);
    }
}
