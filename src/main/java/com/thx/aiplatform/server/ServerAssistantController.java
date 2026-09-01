package com.thx.aiplatform.server;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/server/v1")
class ServerAssistantController {

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

    private void sendChunk(SseEmitter emitter, AtomicBoolean terminated, AtomicInteger length, String chunk) {
        if (terminated.get() || chunk == null || chunk.isEmpty()) return;
        try {
            emitter.send(SseEmitter.event().data(chunk, UTF8_TEXT));
            length.addAndGet(chunk.length());
        } catch (IOException exception) {
            if (terminated.compareAndSet(false, true)) emitter.completeWithError(exception);
        }
    }

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

    private Object pendingAction(String conversationId) {
        PendingServerOperationView operation = operationService.findForConversation(conversationId);
        return operation != null ? operation : commandProposalService.findForConversation(conversationId);
    }
}
