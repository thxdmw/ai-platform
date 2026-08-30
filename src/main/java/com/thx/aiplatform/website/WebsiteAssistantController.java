package com.thx.aiplatform.website;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/public/v1/website")
public class WebsiteAssistantController {

    private static final Logger log = LoggerFactory.getLogger(WebsiteAssistantController.class);
    private static final String DONE_MARKER = "[DONE]";
    private static final String EMPTY_ANSWER = "抱歉，我暂时无法回答这个问题，请换个问法再试。";
    private static final String UNAVAILABLE_ANSWER = "抱歉，网站助手暂时不可用，请稍后重试。";
    private static final MediaType UTF8_TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final WebsiteAssistantService assistantService;
    private final WebsiteAssistantProperties properties;

    public WebsiteAssistantController(
            WebsiteAssistantService assistantService,
            WebsiteAssistantProperties properties
    ) {
        this.assistantService = assistantService;
        this.properties = properties;
    }

    @PostMapping(
            value = "/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"
    )
    public SseEmitter sendMessage(@Valid @RequestBody WebsiteChatRequest request) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger contentLength = new AtomicInteger();

        emitter.onTimeout(() -> terminate(emitter, terminated, UNAVAILABLE_ANSWER));
        emitter.onError(error -> terminated.set(true));
        emitter.onCompletion(() -> terminated.set(true));

        assistantService.stream(request).subscribe(
                chunk -> sendChunk(emitter, terminated, contentLength, chunk),
                error -> {
                    log.error("网站助手模型调用失败，conversationId={}", request.conversationId(), error);
                    terminate(emitter, terminated, UNAVAILABLE_ANSWER);
                },
                () -> terminate(emitter, terminated, contentLength.get() == 0 ? EMPTY_ANSWER : null)
        );
        return emitter;
    }

    private void sendChunk(
            SseEmitter emitter,
            AtomicBoolean terminated,
            AtomicInteger contentLength,
            String chunk
    ) {
        if (terminated.get() || chunk == null || chunk.isEmpty()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(chunk, UTF8_TEXT));
            contentLength.addAndGet(chunk.length());
        } catch (IOException exception) {
            if (terminated.compareAndSet(false, true)) {
                log.debug("网站助手 SSE 连接已断开：{}", exception.getMessage());
                emitter.completeWithError(exception);
            }
        }
    }

    private void terminate(SseEmitter emitter, AtomicBoolean terminated, String finalMessage) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        try {
            if (finalMessage != null) {
                emitter.send(SseEmitter.event().data(finalMessage, UTF8_TEXT));
            }
            emitter.send(SseEmitter.event().data(DONE_MARKER, UTF8_TEXT));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
