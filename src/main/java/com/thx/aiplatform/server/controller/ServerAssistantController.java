package com.thx.aiplatform.server.controller;

import com.thx.aiplatform.platform.AssistantStreamEvent;
import com.thx.aiplatform.server.config.ServerAssistantProperties;
import com.thx.aiplatform.server.model.PendingServerOperationView;
import com.thx.aiplatform.server.model.ServerChatRequest;
import com.thx.aiplatform.server.model.ServerContinuationRequest;
import com.thx.aiplatform.server.service.ServerAssistantService;
import com.thx.aiplatform.server.service.ServerCommandProposalService;
import com.thx.aiplatform.server.service.ServerOperationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话与事件流的 HTTP 入口。手动用 SseEmitter 桥接反应式的 Flux 流，而不是直接返回
 * Flux<ServerEvent>：需要在超时/错误/完成三种终止路径上统一收尾（发 [DONE] 标记、
 * 附带当轮的待确认 action 事件），并保证同一连接只被终止一次。
 */
@RestController
@RequestMapping("/api/server/v1")
public class ServerAssistantController {

    private static final Logger log = LoggerFactory.getLogger(ServerAssistantController.class);
    private static final MediaType UTF8_TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private final ServerAssistantService assistantService;
    private final ServerOperationService operationService;
    private final ServerAssistantProperties properties;
    private final ServerCommandProposalService commandProposalService;

    ServerAssistantController(ServerAssistantService assistantService, ServerOperationService operationService,
                              ServerAssistantProperties properties,
                              ServerCommandProposalService commandProposalService) {
        this.assistantService = assistantService;
        this.operationService = operationService;
        this.properties = properties;
        this.commandProposalService = commandProposalService;
    }

    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    SseEmitter sendMessage(@Valid @RequestBody ServerChatRequest request) {
        return stream(request.conversationId(),
                reactor.core.publisher.Flux.defer(() -> assistantService.stream(request)));
    }

    @PostMapping(value = "/messages/continue", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    SseEmitter continueAfterAction(@Valid @RequestBody ServerContinuationRequest request) {
        return stream(request.conversationId(),
                reactor.core.publisher.Flux.defer(() -> assistantService.continueAfterAction(request)));
    }

    /**
     * 订阅模型流并桥接 SseEmitter。三种终止来源（超时、订阅错误、正常完成）都汇入
     * terminate；正常完成且本轮没有产出任何文本时，给用户一句「换个问法再试」而不是
     * 静默结束。
     */
    private SseEmitter stream(String conversationId, reactor.core.publisher.Flux<AssistantStreamEvent> response) {
        // Servlet 异步超时是绝对时长，不会因模型持续吐 token 而重置。容器层禁用绝对超时，
        // 再由 Reactor timeout 实现真正的「连续无事件超时」，长推理不会在中途被误杀。
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean terminated = new AtomicBoolean();
        AtomicBoolean firstEvent = new AtomicBoolean();
        AtomicInteger contentLength = new AtomicInteger();
        AtomicInteger reasoningLength = new AtomicInteger();
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        long startedAt = System.nanoTime();
        log.info("服务器助手请求开始，conversationId={}", conversationId);
        emitter.onTimeout(() -> disconnect(conversationId, "Servlet 超时", emitter, terminated, subscription));
        emitter.onError(error -> disconnect(conversationId, "客户端连接错误", emitter, terminated, subscription));
        emitter.onCompletion(() -> disconnect(conversationId, "客户端连接完成", emitter, terminated, subscription));
        Disposable disposable = response.timeout(properties.getSseTimeout()).subscribe(
                event -> sendEvent(emitter, terminated, firstEvent, startedAt, contentLength, reasoningLength,
                        conversationId, event),
                error -> {
                    log.error("服务器助手模型调用失败，conversationId={}", conversationId, error);
                    String message = error instanceof TimeoutException
                            ? "服务器助手长时间没有返回新内容，请稍后重试。"
                            : "服务器助手暂时不可用，请稍后重试。";
                    terminate(emitter, terminated, message, null, subscription, conversationId,
                            contentLength.get(), reasoningLength.get(), startedAt);
                },
                () -> terminate(emitter, terminated,
                        contentLength.get() == 0 ? "暂时没有得到回答，请换个问法再试。" : null,
                        pendingAction(conversationId), subscription, conversationId,
                        contentLength.get(), reasoningLength.get(), startedAt)
        );
        subscription.set(disposable);
        if (terminated.get()) disposable.dispose();
        return emitter;
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteConversation(@PathVariable String conversationId) {
        assistantService.deleteConversation(conversationId);
    }

    /**
     * 发送一个文本块并累计长度。客户端已断开后继续 send 只会累积异常，因此用 CAS 保证
     * 首次失败后连接只被终止一次。
     */
    private void sendEvent(SseEmitter emitter, AtomicBoolean terminated, AtomicBoolean firstEvent, long startedAt,
                           AtomicInteger contentLength, AtomicInteger reasoningLength,
                           String conversationId, AssistantStreamEvent event) {
        if (terminated.get() || event == null || event.content() == null || event.content().isEmpty()) return;
        try {
            if (event.type() == AssistantStreamEvent.Type.REASONING) {
                emitter.send(SseEmitter.event().name("reasoning").data(event.content(), UTF8_TEXT));
                reasoningLength.addAndGet(event.content().length());
            } else {
                emitter.send(SseEmitter.event().data(event.content(), UTF8_TEXT));
                contentLength.addAndGet(event.content().length());
            }
            if (firstEvent.compareAndSet(false, true)) {
                log.info("服务器助手收到首个模型事件，conversationId={}，首包耗时={}ms",
                        conversationId, elapsedMillis(startedAt));
            }
        } catch (IOException | IllegalStateException exception) {
            if (terminated.compareAndSet(false, true)) safeCompleteWithError(emitter, exception);
        }
    }

    /**
     * 统一收尾：先发可选的最末消息，再发待确认 action 事件，最后以 [DONE] 标记结束——
     * 前端依赖 [DONE] 判定流结束并据此渲染确认按钮。
     */
    private void terminate(SseEmitter emitter, AtomicBoolean terminated, String finalMessage,
                           Object operation, AtomicReference<Disposable> subscription, String conversationId,
                           int contentLength, int reasoningLength, long startedAt) {
        if (!terminated.compareAndSet(false, true)) return;
        try {
            if (finalMessage != null) emitter.send(SseEmitter.event().data(finalMessage, UTF8_TEXT));
            if (operation != null) emitter.send(SseEmitter.event().name("action").data(operation, MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event().data("[DONE]", UTF8_TEXT));
            emitter.complete();
            log.info("服务器助手请求完成，conversationId={}，正文字符={}，推理字符={}，耗时={}ms，待确认操作={}",
                    conversationId, contentLength, reasoningLength, elapsedMillis(startedAt), operation != null);
        } catch (IOException | IllegalStateException exception) {
            safeCompleteWithError(emitter, exception);
        } finally {
            Disposable disposable = subscription.get();
            if (disposable != null && !disposable.isDisposed()) disposable.dispose();
        }
    }

    private void disconnect(String conversationId, String reason, SseEmitter emitter, AtomicBoolean terminated,
                            AtomicReference<Disposable> subscription) {
        if (!terminated.compareAndSet(false, true)) return;
        Disposable disposable = subscription.get();
        if (disposable != null) disposable.dispose();
        log.info("服务器助手 SSE 已结束，conversationId={}，原因={}", conversationId, reason);
        try { emitter.complete(); }
        catch (IllegalStateException ignored) { /* 容器已结束时无需二次完成。 */ }
    }

    private void safeCompleteWithError(SseEmitter emitter, Throwable error) {
        try { emitter.completeWithError(error); }
        catch (IllegalStateException ignored) { /* 客户端断开与模型完成可能并发，先完成者获胜。 */ }
    }

    private long elapsedMillis(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    /**
     * 流结束后查询该会话残留的待确认操作/提议，交给 terminate 作为 action 事件推给
     * 页面——这是「服务端生成一次性确认选项 → 用户点击」闭环的最后一环。
     */
    private Object pendingAction(String conversationId) {
        PendingServerOperationView operation = operationService.findForConversation(conversationId);
        return operation != null ? operation : commandProposalService.findForConversation(conversationId);
    }
}
