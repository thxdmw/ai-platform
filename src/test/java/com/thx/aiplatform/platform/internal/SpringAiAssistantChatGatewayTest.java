package com.thx.aiplatform.platform.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thx.aiplatform.platform.AssistantStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAssistantChatGatewayTest {

    @Test
    void shouldReadCommonReasoningMetadataWithoutTreatingContentAsReasoning() {
        var output = AssistantMessage.builder()
                .content("最终答案")
                .properties(Map.of("reasoning_details", List.of(Map.of("text", "先检查状态"))))
                .build();
        var mapper = new SpringAiAssistantChatGateway.StreamEventMapper();

        List<AssistantStreamEvent> events = mapper.map(new ChatResponse(List.of(new Generation(output))));
        events = new java.util.ArrayList<>(events);
        events.addAll(mapper.finish());

        assertThat(events).containsExactly(
                AssistantStreamEvent.reasoning("先检查状态"),
                AssistantStreamEvent.content("最终答案"));
    }

    @Test
    void shouldSplitThinkTagsEvenWhenMarkersCrossStreamChunks() {
        var mapper = new SpringAiAssistantChatGateway.StreamEventMapper();
        List<AssistantStreamEvent> events = new java.util.ArrayList<>();
        events.addAll(mapper.map(response("<thi")));
        events.addAll(mapper.map(response("nk>检查配置</thi")));
        events.addAll(mapper.map(response("nk>这是结论")));
        events.addAll(mapper.finish());

        assertThat(events).containsExactly(
                AssistantStreamEvent.reasoning("检查配置"),
                AssistantStreamEvent.content("这是结论"));
    }

    @Test
    void shouldPreserveOpenAiMessageAndReadReasoningDetailsAlias() throws Exception {
        ObjectMapper delegate = new ObjectMapper();
        var deserializer = new SpringAiAssistantChatGateway.CompatibleOpenAiMessageDeserializer(delegate);
        var module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(OpenAiApi.ChatCompletionMessage.class, deserializer);
        ObjectMapper compatible = new ObjectMapper().registerModule(module);

        OpenAiApi.ChatCompletionMessage message = compatible.readValue("""
                {"role":"assistant","content":"最终答案","reasoning_details":[{"text":"先检查服务"}]}
                """, OpenAiApi.ChatCompletionMessage.class);

        assertThat(message.content()).isEqualTo("最终答案");
        assertThat(message.reasoningContent()).isEqualTo("先检查服务");
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(content).build())));
    }
}
