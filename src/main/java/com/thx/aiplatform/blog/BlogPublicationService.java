package com.thx.aiplatform.blog;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    PendingPublicationView findForConversation(String conversationId) {
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

    private void cleanupExpired() {
        Instant now = clock.instant();
        pendingActions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

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
