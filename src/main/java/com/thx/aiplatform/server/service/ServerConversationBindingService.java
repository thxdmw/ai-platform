package com.thx.aiplatform.server.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话 → 服务器绑定表：一个对话一旦关联某台服务器就不可更换——模型会话的 system prompt
 * 与工具上下文都绑定该服务器，切换会让模型沿用上一台服务器的命令 ID 和上下文而串台。
 * 绑定是纯内存状态，用 accessOrder 的 LinkedHashMap 做 LRU 淘汰，防止长期会话把内存
 * 涨满；服务器/命令是持久化的，绑定丢了一台只是下次需重选，可以接受。
 */
@Service
public class ServerConversationBindingService {

    private static final int MAX_BINDINGS = 1000;
    private final Map<String, String> bindings = new LinkedHashMap<>(32, 0.75f, true);

    /**
     * 绑定或重申绑定。同一对话换服务器直接拒绝（用户需新建对话）；超过上限时按最久
     * 未访问淘汰，与删除会话时的显式 remove 一起构成双保险。
     */
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
