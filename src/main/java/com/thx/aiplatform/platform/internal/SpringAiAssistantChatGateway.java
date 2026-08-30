package com.thx.aiplatform.platform.internal;

import com.thx.aiplatform.platform.AssistantChatCommand;
import com.thx.aiplatform.platform.AssistantChatGateway;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
class SpringAiAssistantChatGateway implements AssistantChatGateway {

    private static final int MEMORY_MESSAGE_LIMIT = 20;

    private final ChatClient chatClient;

    SpringAiAssistantChatGateway(ChatClient.Builder builder) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
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

    @Override
    public Flux<String> stream(AssistantChatCommand command, Object... tools) {
        String scopedConversationId = command.assistantId() + ":" + command.conversationId();
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
}
