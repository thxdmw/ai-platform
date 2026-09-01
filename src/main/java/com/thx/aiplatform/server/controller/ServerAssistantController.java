package com.thx.aiplatform.server.controller;
import com.thx.aiplatform.server.service.ServerOperationService;
import com.thx.aiplatform.server.service.ServerCommandProposalService;
import com.thx.aiplatform.server.service.ServerAssistantService;
import com.thx.aiplatform.server.model.ServerContinuationRequest;
import com.thx.aiplatform.server.model.ServerChatRequest;
import com.thx.aiplatform.server.model.PendingServerOperationView;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        return stream(request.conversationId(), assistantService.stream(request));
    }

    @PostMapping(value = "/messages/continue", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    SseEmitter continueAfterAction(@Valid @RequestBody ServerContinuationRequest request) {
        return stream(request.conversationId(), assistantService.continueAfterAction(request));
    }

    /**
     * 订阅模型流并桥接 SseEmitter。三种终止来源（超时、订阅错误、正常完成）都汇入
     * terminate；正常完成且本轮没有产出任何文本时，给用户一句「换个问法再试」而不是
     * 静默结束。
     */
    private SseEmitter stream(String conversationId, reactor.core.publisher.Flux<String> response) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        AtomicBoolean terminated = new AtomicBoolean();
        AtomicInteger contentLength = new AtomicInteger();
        emitter.onTimeout(() -> terminate(emitter, terminated, "服务器助手响应超时，请稍后重试。", null));
        emitter.onError(error -> terminated.set(true));
        emitter.onCompletion(() -> terminated.set(true));
        response.subscribe(
                chunk -> sendChunk(emitter, terminated, contentLength, chunk),
                error -> {
                    log.error("服务器助手模型调用失败，conversationId={}", conversationId, error);
                    terminate(emitter, terminated, "服务器助手暂时不可用，请稍后重试。", null);
                },
                () -> terminate(emitter, terminated,
                        contentLength.get() == 0 ? "暂时没有得到回答，请换个问法再试。" : null,
                        pendingAction(conversationId))
        );
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
    private void sendChunk(SseEmitter emitter, AtomicBoolean terminated, AtomicInteger length, String chunk) {
        if (terminated.get() || chunk == null || chunk.isEmpty()) return;
        try {
            emitter.send(SseEmitter.event().data(chunk, UTF8_TEXT));
            length.addAndGet(chunk.length());
        } catch (IOException exception) {
            if (terminated.compareAndSet(false, true)) emitter.completeWithError(exception);
        }
    }

    /**
     * 统一收尾：先发可选的最末消息，再发待确认 action 事件，最后以 [DONE] 标记结束——
     * 前端依赖 [DONE] 判定流结束并据此渲染确认按钮。
     */
    private void terminate(SseEmitter emitter, AtomicBoolean terminated, String finalMessage,
                           Object operation) {
        if (!terminated.compareAndSet(false, true)) return;
        try {
            if (finalMessage != null) emitter.send(SseEmitter.event().data(finalMessage, UTF8_TEXT));
            if (operation != null) emitter.send(SseEmitter.event().name("action").data(operation, MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event().data("[DONE]", UTF8_TEXT));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
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
