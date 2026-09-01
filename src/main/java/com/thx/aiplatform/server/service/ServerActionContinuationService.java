package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话「续跑」凭证服务：模型一轮任务被打断后（危险命令已确认执行、命令已确认添加），
 * 服务端把「系统可信事件」消息暂存并签发一次性票据，用户点击续跑时服务端校验票据、
 * 取回消息交还给模型继续原任务。
 *
 * <p>为什么消息必须由服务端保存而不是让模型自己携带：模型的记忆与输出都不可信，只有
 * 服务端签发的可信上下文才能约束模型「继续完成原任务、不得自动重试」等行为。</p>
 *
 * <p>票据只存在内存 ConcurrentHashMap 中（有效期由 approvalTtl 控制），重启即全部失效——
 * 这是刻意接受的取舍：服务器、命令等数据都在数据库，内存里只有「待续跑的上下文」，
 * 丢了最多让用户重新发起一次请求，不值得为此引入持久化。</p>
 */
@Service
public class ServerActionContinuationService {

    private static final int MAX_CONTINUATIONS = 100;
    private final ServerAssistantProperties properties;
    private final Clock clock;
    private final Map<String, Continuation> continuations = new ConcurrentHashMap<>();

    @Autowired
    ServerActionContinuationService(ServerAssistantProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ServerActionContinuationService(ServerAssistantProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 签发票据。先作废同一对话的旧票据：防止旧票据在过期前被消费，把上一轮的过时上下文
     * 带进新一轮；容量满时淘汰最早到期的一张而不是拒绝，保证连续操作不被上限卡死。
     */
    String prepare(String conversationId, String serverId, String message) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (continuations.size() >= MAX_CONTINUATIONS) {
            continuations.entrySet().stream()
                    .min(Map.Entry.comparingByValue((left, right) -> left.expiresAt().compareTo(right.expiresAt())))
                    .ifPresent(entry -> continuations.remove(entry.getKey(), entry.getValue()));
        }
        String id = UUID.randomUUID().toString();
        continuations.put(id, new Continuation(conversationId, serverId, message,
                clock.instant().plus(properties.getApprovalTtl())));
        return id;
    }

    /**
     * 消费票据。必须用带值比较的原子 remove 而不是「先 get 再 remove」：票据语义是
     * 只消费一次，并发场景下只有其中一个请求能真正移除成功；同时校验会话与服务器绑定，
     * 防止票据被另一个会话盗用。
     */
    String consume(String id, String conversationId, String serverId) {
        Continuation continuation = continuations.get(id);
        if (continuation == null) throw new IllegalArgumentException("对话续跑凭证不存在或已使用");
        if (!continuation.expiresAt().isAfter(clock.instant())) {
            continuations.remove(id, continuation);
            throw new IllegalArgumentException("对话续跑凭证已过期，请重新发起请求");
        }
        if (!continuation.conversationId().equals(conversationId) || !continuation.serverId().equals(serverId)) {
            throw new IllegalArgumentException("对话续跑凭证与当前会话不匹配");
        }
        if (!continuations.remove(id, continuation)) {
            throw new IllegalArgumentException("对话续跑凭证不存在或已使用");
        }
        return continuation.message();
    }

    void cancelForConversation(String conversationId) {
        continuations.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        continuations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Continuation(String conversationId, String serverId, String message, Instant expiresAt) { }
}
