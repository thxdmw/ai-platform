package com.thx.aiplatform.platform;

import reactor.core.publisher.Flux;

public interface AssistantChatGateway {

    Flux<String> stream(AssistantChatCommand command);
}
