package com.thx.aiplatform.platform;

import reactor.core.publisher.Flux;

/**
 * 助手对话的统一门面，platform 模块对上层模块（如 website）暴露的唯一对话通道。
 * <p>上层只依赖本接口、不感知 Spring AI 及具体实现，因此更换模型后端时上层模块零改动；
 * 实现类放在 platform.internal 包且对模块外不可见（见 {@code SpringAiAssistantChatGateway}），
 * 保证「跨助手只走契约」的依赖方向不被绕过。</p>
 */
public interface AssistantChatGateway {

    Flux<String> stream(AssistantChatCommand command);

    // 带 tools 的重载按需给模型挂可调用工具；不带则退化为纯对话。
    Flux<String> stream(AssistantChatCommand command, Object... tools);

    void clear(String assistantId, String conversationId);
}
