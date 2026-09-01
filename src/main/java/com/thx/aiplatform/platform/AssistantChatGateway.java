package com.thx.aiplatform.platform;

import reactor.core.publisher.Flux;

public interface AssistantChatGateway {

    Flux<String> stream(AssistantChatCommand command);

    Flux<String> stream(AssistantChatCommand command, Object... tools);

    void clear(String assistantId, String conversationId);
}
