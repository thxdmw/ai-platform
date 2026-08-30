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

    public PendingPublicationView prepare(BlogPublicationRequest rawRequest) {
        cleanupExpired();
        if (pendingActions.size() >= MAX_PENDING_ACTIONS) {
            throw new IllegalStateException("待审批发布任务过多，请稍后重试");
        }

        BlogPublicationRequest request = rawRequest.normalized();
        String actionId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(properties.getApprovalTtl());
        PendingPublication pending = new PendingPublication(actionId, request, expiresAt);
        pendingActions.put(actionId, pending);
        return toView(pending);
    }

    public PublicationResult approve(String actionId, String confirmation) {
        if (!"发布".equals(confirmation)) {
            throw new IllegalArgumentException("必须输入“发布”才能确认操作");
        }

        // 先原子移除再调用上游，避免双击或并发请求重复发布同一篇文章。
        PendingPublication pending = pendingActions.remove(actionId);
        if (pending == null) {
            throw new IllegalArgumentException("待审批任务不存在或已处理");
        }
        if (!pending.expiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("待审批任务已过期，请重新创建");
        }

        try {
            String response = apiClient.postForm("/publishBlog", BlogApiClient.publicationParameters(pending.request()));
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

    private PendingPublicationView toView(PendingPublication pending) {
        BlogPublicationRequest request = pending.request();
        return new PendingPublicationView(
                pending.actionId(),
                request.title(),
                request.description(),
                request.contentMd().length(),
                pending.expiresAt(),
                "PENDING_APPROVAL"
        );
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        pendingActions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String truncate(String response) {
        if (response == null || response.length() <= UPSTREAM_RESPONSE_LIMIT) {
            return response;
        }
        return response.substring(0, UPSTREAM_RESPONSE_LIMIT) + "...[响应已截断]";
    }

    private record PendingPublication(
            String actionId,
            BlogPublicationRequest request,
            Instant expiresAt
    ) {
    }
}
