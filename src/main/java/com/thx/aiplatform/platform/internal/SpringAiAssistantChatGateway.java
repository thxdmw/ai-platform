package com.thx.aiplatform.platform.internal;

import com.thx.aiplatform.platform.AssistantChatCommand;
import com.thx.aiplatform.platform.AssistantChatGateway;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * {@link AssistantChatGateway} 的 Spring AI 实现，位于 platform.internal 内部包。
 * <p>实现类刻意保持包私有：其他模块只能按接口编程，无法注入或 new 出本实现，
 * 从而保证「模型接入方式可被整体替换」这件事不被上层代码耦合住。</p>
 * <p>记忆用 {@link MessageWindowChatMemory} 固定保留最近 {@value #MEMORY_MESSAGE_LIMIT} 条消息：
 * 过长上下文既烧 token 又引入无关噪音，20 条是对「够用」和「廉价」的折中。</p>
 */
@Component
class SpringAiAssistantChatGateway implements AssistantChatGateway {

    private static final int MEMORY_MESSAGE_LIMIT = 20;

    private final ChatClient chatClient;
    private final ChatMemory memory;

    // ChatClient 与记忆 Advisor 在构造期一次绑定：调用方只能按 command 对话，
    // 无法绕过记忆层裸调模型，避免各模块各自管理上下文导致行为不一致。
    SpringAiAssistantChatGateway(ChatClient.Builder builder) {
        this.memory = MessageWindowChatMemory.builder()
                .maxMessages(MEMORY_MESSAGE_LIMIT)
                .build();
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    @Override
    public Flux<String> stream(AssistantChatCommand command) {
        return stream(command, new Object[0]);
    }

    // conversationId 加 assistantId 前缀再作为全局记忆 key，是「多助手共享同一 ChatMemory
    // 而不串记忆」的关键；过滤空 content 是为了不让模型吐出的空白/换行 chunk 变成无意义 SSE 事件。
    @Override
    public Flux<String> stream(AssistantChatCommand command, Object... tools) {
        String scopedConversationId = scopedConversationId(command.assistantId(), command.conversationId());
        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(command.systemPrompt())
                .user(command.userMessage())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId));
        if (tools != null && tools.length > 0) {
            request.tools(tools);
        }
        return request
                .stream()
                .content()
                .filter(content -> content != null && !content.isBlank());
    }

    // clear 必须沿用与写入相同的 scoped 规则，否则会清错「别的助手/会话」的记忆。
    @Override
    public void clear(String assistantId, String conversationId) {
        memory.clear(scopedConversationId(assistantId, conversationId));
    }

    private String scopedConversationId(String assistantId, String conversationId) {
        return assistantId + ":" + conversationId;
    }
}
