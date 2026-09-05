package com.thx.aiplatform.blog.service;
import com.thx.aiplatform.blog.vo.PublicationResult;
import com.thx.aiplatform.blog.vo.PendingPublicationView;
import com.thx.aiplatform.blog.dto.BlogPublicationRequest;
import com.thx.aiplatform.blog.config.BlogAssistantProperties;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布候选（PendingPublication）的生命周期管理：prepare 生成带 TTL 的候选并绑定会话，
 * approve 先原子移除再调上游，保证同一候选至多发布一次；过期与对话切换都会使候选失效。
 * 候选存于进程内 ConcurrentHashMap，服务重启即丢失——这符合「一次性短时确认」的语义，
 * 也免去了为一次性对象引入持久化的复杂度。
 */
@Service
public class BlogPublicationService {

    private static final int MAX_PENDING_ACTIONS = 100;
    private static final int UPSTREAM_RESPONSE_LIMIT = 4_000;

    private final BlogApiClient apiClient;
    private final BlogAssistantProperties properties;
    private final Clock clock;
    private final Map<String, PendingPublication> pendingActions = new ConcurrentHashMap<>();

    @Autowired
    public BlogPublicationService(BlogApiClient apiClient, BlogAssistantProperties properties) {
        this(apiClient, properties, Clock.systemUTC());
    }

    BlogPublicationService(BlogApiClient apiClient, BlogAssistantProperties properties, Clock clock) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 生成前先清过期、作废同会话旧候选，并把总量压在 MAX_PENDING_ACTIONS 以内：
     * 内存存储没有天然上限，必须防住模型反复生成把内存撑爆。
     */
    public PendingPublicationView prepare(String conversationId, BlogPublicationRequest rawRequest) {
        cleanupExpired();
        cancelForConversation(conversationId);
        if (pendingActions.size() >= MAX_PENDING_ACTIONS) {
            throw new IllegalStateException("待确认发布选项过多，请稍后重试");
        }

        validate(rawRequest);
        BlogPublicationRequest request = rawRequest.normalized();
        String actionId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(properties.getApprovalTtl());
        PendingPublication pending = new PendingPublication(actionId, conversationId, request, expiresAt);
        pendingActions.put(actionId, pending);
        return toView(pending);
    }

    public PublicationResult approve(String actionId) {
        // 先原子移除再调用上游，避免双击或并发请求重复发布同一篇文章。
        PendingPublication pending = pendingActions.remove(actionId);
        if (pending == null) {
            throw new IllegalArgumentException("发布选项不存在或已处理");
        }
        if (!pending.expiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("发布选项已过期，请重新生成文章");
        }

        try {
            String response = apiClient.publish(pending.request());
            return new PublicationResult(actionId, true, "博客已发布", truncate(response));
        } catch (RuntimeException exception) {
            // 网络异常时无法判断上游是否已接收，禁止自动重试以免形成重复文章。
            return new PublicationResult(
                    actionId,
                    false,
                    "发布结果不确定，请先到博客后台确认后再决定是否重试：" + exception.getMessage(),
                    null
            );
        }
    }

    PendingPublicationView find(String actionId) {
        PendingPublication pending = pendingActions.get(actionId);
        if (pending == null || !pending.expiresAt().isAfter(clock.instant())) {
            pendingActions.remove(actionId);
            return null;
        }
        return toView(pending);
    }

    /**
     * 专供 SSE 流结束时取候选推给前端：聊天记录在浏览器本地、服务端不持久化，
     * 前端要渲染「发布/取消」按钮只能依赖这条查询，而不是回放历史。
     */
    public PendingPublicationView findForConversation(String conversationId) {
        cleanupExpired();
        return pendingActions.values().stream()
                .filter(pending -> pending.conversationId().equals(conversationId))
                .findFirst()
                .map(this::toView)
                .orElse(null);
    }

    public void cancel(String actionId) {
        pendingActions.remove(actionId);
    }

    void cancelForConversation(String conversationId) {
        pendingActions.entrySet().removeIf(entry -> entry.getValue().conversationId().equals(conversationId));
    }

    /**
     * 刻意不带正文全量，只给摘要与长度：页面确认时不需要草稿正文，减少跨层传输体积。
     */
    private PendingPublicationView toView(PendingPublication pending) {
        BlogPublicationRequest request = pending.request();
        return new PendingPublicationView(
                pending.actionId(),
                request.title(),
                request.description(),
                request.categoryId(),
                request.tagIds(),
                request.contentMd().length(),
                pending.expiresAt(),
                "PENDING_APPROVAL"
        );
    }

    /**
     * 惰性清理，不做定时任务：候选本身是短时对象，各入口顺带清一遍就够，过期条目下次访问时自然消失。
     */
    private void cleanupExpired() {
        Instant now = clock.instant();
        pendingActions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    /**
     * prepare 由模型工具调用而非 @Valid 校验的控制器入口，请求体上的注解校验在这里不生效，
     * 必须在此做同样的非空与长度兜底。
     */
    private void validate(BlogPublicationRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("文章标题不能为空");
        }
        if (request.contentMd() == null || request.contentMd().isBlank()) {
            throw new IllegalArgumentException("Markdown 正文不能为空");
        }
        if (request.title().length() > 200 || request.contentMd().length() > 100_000) {
            throw new IllegalArgumentException("发布内容超过允许长度");
        }
    }

    /**
     * 发布响应只用于页面回显成败，全文既浪费带宽也没有消费方；截断到 4KB 足够判断。
     */
    private String truncate(String response) {
        if (response == null || response.length() <= UPSTREAM_RESPONSE_LIMIT) {
            return response;
        }
        return response.substring(0, UPSTREAM_RESPONSE_LIMIT) + "...[响应已截断]";
    }

    private record PendingPublication(
            String actionId,
            String conversationId,
            BlogPublicationRequest request,
            Instant expiresAt
    ) {
    }
}
