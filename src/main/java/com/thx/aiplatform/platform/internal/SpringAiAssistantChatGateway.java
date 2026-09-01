package com.thx.aiplatform.platform.internal;

import com.thx.aiplatform.platform.AssistantChatCommand;
import com.thx.aiplatform.platform.AssistantChatGateway;
import com.thx.aiplatform.platform.AssistantStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    private static final Logger log = LoggerFactory.getLogger(SpringAiAssistantChatGateway.class);

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
        return request(command, tools).stream().content().filter(content -> content != null && !content.isBlank());
    }

    @Override
    public Flux<AssistantStreamEvent> streamEvents(AssistantChatCommand command, Object... tools) {
        long startedAt = System.nanoTime();
        return request(command, tools).stream().chatResponse()
                .concatMap(response -> Flux.fromIterable(toEvents(response)))
                .doOnSubscribe(ignored -> log.info("模型流开始，assistantId={}，conversationId={}，model={}，provider={}",
                        command.assistantId(), command.conversationId(), modelName(command), providerHost(command)))
                .doOnComplete(() -> log.info("模型流完成，assistantId={}，conversationId={}，耗时={}ms",
                        command.assistantId(), command.conversationId(), elapsedMillis(startedAt)))
                .doOnCancel(() -> log.info("模型流取消，assistantId={}，conversationId={}，耗时={}ms",
                        command.assistantId(), command.conversationId(), elapsedMillis(startedAt)))
                .doOnError(error -> log.error("模型流失败，assistantId={}，conversationId={}，model={}，耗时={}ms",
                        command.assistantId(), command.conversationId(), modelName(command), elapsedMillis(startedAt), error));
    }

    private ChatClient.ChatClientRequestSpec request(AssistantChatCommand command, Object... tools) {
        String scopedConversationId = scopedConversationId(command.assistantId(), command.conversationId());
        ChatClient selectedClient = command.modelConnection() == null ? chatClient : customClient(command);
        ChatClient.ChatClientRequestSpec request = selectedClient.prompt()
                .system(command.systemPrompt())
                .user(command.userMessage())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, scopedConversationId));
        if (tools != null && tools.length > 0) {
            request.tools(tools);
        }
        return request;
    }

    /**
     * 自定义连接按请求构造，避免把 API 密钥作为缓存键或长期保留在全局对象中。
     */
    private ChatClient customClient(AssistantChatCommand command) {
        var connection = command.modelConnection();
        return switch (connection.apiProtocol()) {
            case "openai-completions" -> customOpenAiClient(command);
            case "anthropic-messages" -> customAnthropicClient(command);
            case "openai-responses" -> throw new IllegalArgumentException(
                    "当前 Spring AI 版本暂不支持 openai-responses 的服务器工具调用，请改用 openai-completions");
            default -> throw new IllegalArgumentException("不支持的模型协议：" + connection.apiProtocol());
        };
    }

    private ChatClient customOpenAiClient(AssistantChatCommand command) {
        var connection = command.modelConnection();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(connection.baseUrl())
                .apiKey(connection.apiKey())
                .completionsPath(connection.chatCompletionsPath())
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(connection.model());
        if (connection.reasoningEffort() != null) options.reasoningEffort(connection.reasoningEffort());
        OpenAiChatModel model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options.build()).build();
        return ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    private ChatClient customAnthropicClient(AssistantChatCommand command) {
        var connection = command.modelConnection();
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(connection.baseUrl())
                .completionsPath(connection.chatCompletionsPath())
                .apiKey(connection.apiKey())
                .build();
        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder().model(connection.model());
        applyAnthropicReasoning(options, connection.reasoningEffort());
        AnthropicChatModel model = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options.build())
                .build();
        return ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    private void applyAnthropicReasoning(AnthropicChatOptions.Builder options, String effort) {
        if (effort == null) return;
        if ("none".equals(effort)) {
            options.thinking(AnthropicApi.ThinkingType.DISABLED, null);
            return;
        }
        int budget = switch (effort) {
            case "minimal", "low" -> 1_024;
            case "medium" -> 2_048;
            case "high" -> 4_096;
            case "xhigh" -> 8_192;
            case "max" -> 16_000;
            default -> throw new IllegalArgumentException("Anthropic 不支持该推理等级：" + effort);
        };
        options.thinking(AnthropicApi.ThinkingType.ENABLED, budget).maxTokens(Math.max(8_192, budget + 4_096));
    }

    // clear 必须沿用与写入相同的 scoped 规则，否则会清错「别的助手/会话」的记忆。
    @Override
    public void clear(String assistantId, String conversationId) {
        memory.clear(scopedConversationId(assistantId, conversationId));
    }

    private String scopedConversationId(String assistantId, String conversationId) {
        return assistantId + ":" + conversationId;
    }

    private List<AssistantStreamEvent> toEvents(org.springframework.ai.chat.model.ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) return List.of();
        var output = response.getResult().getOutput();
        List<AssistantStreamEvent> events = new ArrayList<>(2);
        Object metadataReasoning = output.getMetadata().get("reasoningContent");
        String reasoning = metadataReasoning == null ? null : metadataReasoning.toString();
        if (output instanceof org.springframework.ai.deepseek.DeepSeekAssistantMessage deepSeekMessage) {
            reasoning = deepSeekMessage.getReasoningContent();
        }
        if (output.getMetadata().containsKey("signature")) reasoning = output.getText();
        if (reasoning != null && !reasoning.isBlank()) events.add(AssistantStreamEvent.reasoning(reasoning));
        if (!output.getMetadata().containsKey("signature") && output.getText() != null && !output.getText().isBlank()) {
            events.add(AssistantStreamEvent.content(output.getText()));
        }
        return events;
    }

    private String modelName(AssistantChatCommand command) {
        return command.modelConnection() == null ? "system-default" : command.modelConnection().model();
    }

    private String providerHost(AssistantChatCommand command) {
        if (command.modelConnection() == null) return "system-default";
        try { return URI.create(command.modelConnection().baseUrl()).getHost(); }
        catch (IllegalArgumentException ignored) { return "invalid-host"; }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
