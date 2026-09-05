package com.thx.aiplatform.blog.controller;
import com.thx.aiplatform.blog.service.BlogPublicationService;
import com.thx.aiplatform.blog.service.BlogAssistantService;
import com.thx.aiplatform.blog.vo.PendingPublicationView;
import com.thx.aiplatform.blog.dto.BlogChatRequest;
import com.thx.aiplatform.blog.config.BlogAssistantProperties;

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
 * 博客助手聊天的 REST 入口（瘦控制器）：把模型流式输出转成 SSE 推给前端，
 * 并在流结束时把当前会话待确认的发布选项作为 action 事件追加发送。
 * 聊天记录只存浏览器本地、服务端不持久化，因此「当前有什么待确认」必须随每次流末尾带给前端渲染按钮。
 */
@RestController
@RequestMapping("/api/blog/v1")
public class BlogAssistantController {

    private static final Logger log = LoggerFactory.getLogger(BlogAssistantController.class);
    private static final String DONE_MARKER = "[DONE]";
    private static final String EMPTY_ANSWER = "抱歉，我暂时没有得到回答，请换个问法再试。";
    private static final String UNAVAILABLE_ANSWER = "抱歉，博客助手暂时不可用，请稍后重试。";
    private static final MediaType UTF8_TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final BlogAssistantService assistantService;
    private final BlogAssistantProperties properties;
    private final BlogPublicationService publicationService;

    public BlogAssistantController(BlogAssistantService assistantService, BlogAssistantProperties properties,
                                   BlogPublicationService publicationService) {
        this.assistantService = assistantService;
        this.properties = properties;
        this.publicationService = publicationService;
    }

    /**
     * 流终止状态必须用原子布尔：超时回调、完成回调、流订阅的终止回调可能在不同线程并发触发，
     * 只有原子比较才能保证「结束动作只执行一次」，否则最终消息可能发两次或漏发。
     */
    @PostMapping(
            value = "/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"
    )
    public SseEmitter sendMessage(@Valid @RequestBody BlogChatRequest request) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        AtomicBoolean terminated = new AtomicBoolean(false);
        AtomicInteger contentLength = new AtomicInteger();

        emitter.onTimeout(() -> terminate(emitter, terminated, UNAVAILABLE_ANSWER, null));
        emitter.onError(error -> terminated.set(true));
        emitter.onCompletion(() -> terminated.set(true));

        assistantService.stream(request).subscribe(
                chunk -> sendChunk(emitter, terminated, contentLength, chunk),
                error -> {
                    log.error("博客助手模型调用失败，conversationId={}", request.conversationId(), error);
                    terminate(emitter, terminated, UNAVAILABLE_ANSWER, null);
                },
                () -> terminate(
                        emitter,
                        terminated,
                        contentLength.get() == 0 ? EMPTY_ANSWER : null,
                        publicationService.findForConversation(request.conversationId())
                )
        );
        return emitter;
    }

    /**
     * 客户端断开后 send 会抛 IOException；并发下用 compareAndSet 竞争「谁先发现断开谁收尾」，
     * 保证 completeWithError 只被调用一次。
     */
    private void sendChunk(SseEmitter emitter, AtomicBoolean terminated, AtomicInteger contentLength, String chunk) {
        if (terminated.get() || chunk == null || chunk.isEmpty()) return;
        try {
            emitter.send(SseEmitter.event().data(chunk, UTF8_TEXT));
            contentLength.addAndGet(chunk.length());
        } catch (IOException exception) {
            if (terminated.compareAndSet(false, true)) {
                log.debug("博客助手 SSE 连接已断开：{}", exception.getMessage());
                emitter.completeWithError(exception);
            }
        }
    }

    /**
     * finalMessage 为 null 表示正常结束、不再追加兜底文案；待确认发布选项用名为 action 的事件下发——
     * 发布按钮属于界面动作，不能混进模型输出文本。
     */
    private void terminate(SseEmitter emitter, AtomicBoolean terminated, String finalMessage,
                           PendingPublicationView publication) {
        if (!terminated.compareAndSet(false, true)) return;
        try {
            if (finalMessage != null) emitter.send(SseEmitter.event().data(finalMessage, UTF8_TEXT));
            if (publication != null) {
                emitter.send(SseEmitter.event()
                        .name("action")
                        .data(publication, MediaType.APPLICATION_JSON));
            }
            emitter.send(SseEmitter.event().data(DONE_MARKER, UTF8_TEXT));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
