package com.thx.aiplatform.website.controller;
import com.thx.aiplatform.website.service.WebsiteAssistantService;
import com.thx.aiplatform.website.model.WebsiteChatRequest;
import com.thx.aiplatform.website.config.WebsiteAssistantProperties;

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

/**
 * 网站助手公开接口（/api/public/v1/website/**），无鉴权，安全依赖三层防线：
 * CORS 精确白名单 + 服务端固定助手编号 + IP 限流（均见 config / service 包）。
 * <p>本控制器只做协议适配：把模型返回的 Flux 流桥接为 SSE 输出，统一格式为
 * 「文本 chunk + 可选收尾消息 + [DONE] 结束标记」，前端据此渲染；业务拼装全部
 * 下沉到 {@link WebsiteAssistantService}，保持瘦控制器。</p>
 */
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

    // 模型调用是异步 Flux，必须用 SseEmitter 把流桥接到 Servlet 异步通道；terminated 标志
    // 保证「超时/出错/正常完成」三条结束路径只会执行一次，避免向前端重复发送 [DONE]。
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

    // terminated 一旦置位立即停止转发：SSE 连接已结束或正走向结束，此时继续 send 只会抛异常；
    // 客户端断连走 completeWithError 而非静默 complete，让前端能识别出传输层异常。
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

    // compareAndSet 保证并发下只有一个线程（超时/错误/完成回调）执行收尾；
    // finalMessage 为 null 表示正常完成（已有正文），非 null 表示异常/超时/空回答，需要兜底文案。
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
