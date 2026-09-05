package com.thx.aiplatform.server.service;

/**
 * 会话 → 服务器绑定表接口：一个对话一旦关联某台服务器就不可更换——模型会话的 system
 * prompt 与工具上下文都绑定该服务器，切换会让模型沿用上一台服务器的命令 ID 和上下文
 * 而串台。绑定是纯内存状态（LRU 淘汰），服务器/命令是持久化的，绑定丢了只需重选。
 */
public interface ServerConversationBindingService {

    /** 绑定或重申绑定。同一对话换服务器直接拒绝（用户需新建对话）。 */
    void bind(String conversationId, String serverId);

    void remove(String conversationId);
}