package com.thx.aiplatform.platform.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
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
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
    private final ObjectMapper objectMapper;

    // ChatClient 与记忆 Advisor 在构造期一次绑定：调用方只能按 command 对话，
    // 无法绕过记忆层裸调模型，避免各模块各自管理上下文导致行为不一致。
    SpringAiAssistantChatGateway(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.memory = MessageWindowChatMemory.builder()
                .maxMessages(MEMORY_MESSAGE_LIMIT)
                .build();
        this.objectMapper = objectMapper;
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
        return Flux.defer(() -> {
            long startedAt = System.nanoTime();
            StreamEventMapper mapper = new StreamEventMapper();
            AtomicLong reasoningCharacters = new AtomicLong();
            AtomicLong contentCharacters = new AtomicLong();
            return request(command, tools).stream().chatResponse()
                    .concatMap(response -> Flux.fromIterable(mapper.map(response)))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(mapper.finish())))
                    .doOnNext(event -> {
                        if (event.type() == AssistantStreamEvent.Type.REASONING) reasoningCharacters.addAndGet(event.content().length());
                        else contentCharacters.addAndGet(event.content().length());
                    })
                    .doOnSubscribe(ignored -> log.info("模型流开始，assistantId={}，conversationId={}，model={}，provider={}",
                            command.assistantId(), command.conversationId(), modelName(command), providerHost(command)))
                    .doOnComplete(() -> logCompletion(command, startedAt, reasoningCharacters.get(), contentCharacters.get()))
                    .doOnCancel(() -> log.info("模型流取消，assistantId={}，conversationId={}，耗时={}ms",
                            command.assistantId(), command.conversationId(), elapsedMillis(startedAt)))
                    .doOnError(error -> log.error("模型流失败，assistantId={}，conversationId={}，model={}，耗时={}ms",
                            command.assistantId(), command.conversationId(), modelName(command), elapsedMillis(startedAt), error));
        });
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
                .webClientBuilder(WebClient.builder().codecs(codecs -> codecs.defaultCodecs()
                        .jackson2JsonDecoder(new Jackson2JsonDecoder(compatibleOpenAiObjectMapper()))))
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(connection.model());
        if (connection.reasoningEffort() != null) options.reasoningEffort(openAiReasoningEffort(connection.reasoningEffort()));
        OpenAiChatModel model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options.build()).build();
        return ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    private String openAiReasoningEffort(String effort) {
        // 页面把最高档统一称为 Max；Chat Completions 兼容网关（包括 OpenCode Go）对应的协议值是 xhigh。
        return "max".equals(effort) ? "xhigh" : effort;
    }

    private ObjectMapper compatibleOpenAiObjectMapper() {
        ObjectMapper delegate = objectMapper.copy();
        ObjectMapper compatible = objectMapper.copy();
        SimpleModule module = new SimpleModule("openai-compatible-reasoning");
        module.addDeserializer(OpenAiApi.ChatCompletionMessage.class,
                new CompatibleOpenAiMessageDeserializer(delegate));
        compatible.registerModule(module);
        return compatible;
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

    private void logCompletion(AssistantChatCommand command, long startedAt, long reasoningCharacters, long contentCharacters) {
        log.info("模型流完成，assistantId={}，conversationId={}，耗时={}ms，reasoningChars={}，contentChars={}",
                command.assistantId(), command.conversationId(), elapsedMillis(startedAt), reasoningCharacters, contentCharacters);
        if (expectsReasoning(command) && reasoningCharacters == 0) {
            log.warn("模型未返回独立思考内容，assistantId={}，conversationId={}，model={}，provider={}；"
                            + "请确认模型支持推理且协议会返回 reasoning_content/thinking，而不是把正文误当作思考过程",
                    command.assistantId(), command.conversationId(), modelName(command), providerHost(command));
        }
    }

    private boolean expectsReasoning(AssistantChatCommand command) {
        if (command.modelConnection() == null) return false;
        String effort = command.modelConnection().reasoningEffort();
        return effort != null && !"none".equals(effort);
    }

    /**
     * OpenAI 兼容网关并没有统一思考字段：标准 DeepSeek 使用 reasoning_content，部分聚合网关使用
     * thinking/reasoning_details，另一些会把它包进 &lt;think&gt;。这里仅拆分提供方明确标记的内容，
     * 不会为了让界面“看起来有思考”而把普通正文伪装成推理。
     */
    static final class StreamEventMapper {
        private static final List<String> REASONING_KEYS = List.of(
                "reasoningContent", "reasoning_content", "reasoning", "thinking", "reasoning_details");
        private static final String THINK_START = "<think>";
        private static final String THINK_END = "</think>";
        private final StringBuilder pending = new StringBuilder();
        private boolean insideThink;

        List<AssistantStreamEvent> map(org.springframework.ai.chat.model.ChatResponse response) {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) return List.of();
            var output = response.getResult().getOutput();
            List<AssistantStreamEvent> events = new ArrayList<>(2);
            String reasoning = reasoningFrom(output.getMetadata());
            if (output instanceof org.springframework.ai.deepseek.DeepSeekAssistantMessage deepSeekMessage) {
                reasoning = deepSeekMessage.getReasoningContent();
            }
            boolean anthropicThinking = output.getMetadata().containsKey("signature");
            if (anthropicThinking) reasoning = output.getText();
            if (reasoning != null && !reasoning.isBlank()) events.add(AssistantStreamEvent.reasoning(reasoning));
            if (!anthropicThinking && output.getText() != null && !output.getText().isBlank()) {
                // 没有独立字段时仍兼容聚合网关常见的 XML think 块；普通正文只延迟最多 7 个字符。
                events.addAll(splitText(output.getText()));
            }
            return events;
        }

        List<AssistantStreamEvent> finish() {
            if (pending.isEmpty()) return List.of();
            String value = pending.toString();
            pending.setLength(0);
            return List.of(insideThink ? AssistantStreamEvent.reasoning(value) : AssistantStreamEvent.content(value));
        }

        private List<AssistantStreamEvent> splitText(String chunk) {
            pending.append(chunk);
            List<AssistantStreamEvent> events = new ArrayList<>();
            while (!pending.isEmpty()) {
                String marker = insideThink ? THINK_END : THINK_START;
                int markerIndex = pending.indexOf(marker);
                if (markerIndex >= 0) {
                    add(events, pending.substring(0, markerIndex), insideThink);
                    pending.delete(0, markerIndex + marker.length());
                    insideThink = !insideThink;
                    continue;
                }
                int safeLength = pending.length() - longestMarkerPrefixSuffix(pending, marker);
                if (safeLength <= 0) break;
                add(events, pending.substring(0, safeLength), insideThink);
                pending.delete(0, safeLength);
            }
            return events;
        }

        private int longestMarkerPrefixSuffix(StringBuilder value, String marker) {
            int limit = Math.min(value.length(), marker.length() - 1);
            for (int length = limit; length > 0; length--) {
                if (value.substring(value.length() - length).equals(marker.substring(0, length))) return length;
            }
            return 0;
        }

        private void add(List<AssistantStreamEvent> events, String value, boolean reasoning) {
            if (value.isBlank()) return;
            events.add(reasoning ? AssistantStreamEvent.reasoning(value) : AssistantStreamEvent.content(value));
        }

        private String reasoningFrom(Map<String, Object> metadata) {
            for (String key : REASONING_KEYS) {
                String value = reasoningValue(metadata.get(key));
                if (value != null && !value.isBlank()) return value;
            }
            return null;
        }

        private String reasoningValue(Object value) {
            if (value == null) return null;
            if (value instanceof CharSequence sequence) return sequence.toString();
            if (value instanceof Map<?, ?> map) {
                for (String key : List.of("text", "content", "summary", "reasoning")) {
                    String nested = reasoningValue(map.get(key));
                    if (nested != null && !nested.isBlank()) return nested;
                }
                return null;
            }
            if (value instanceof Collection<?> collection) {
                return collection.stream().map(this::reasoningValue).filter(item -> item != null && !item.isBlank())
                        .reduce((left, right) -> left + right).orElse(null);
            }
            return value.toString();
        }
    }

    /**
     * Spring AI 原生只声明 reasoning_content；OpenCode Go 等兼容网关还可能返回 thinking、reasoning 或
     * reasoning_details。先用原始映射保留工具调用等全部字段，再只补齐缺失的 reasoningContent。
     */
    static final class CompatibleOpenAiMessageDeserializer extends StdDeserializer<OpenAiApi.ChatCompletionMessage> {
        private final ObjectMapper delegate;

        CompatibleOpenAiMessageDeserializer(ObjectMapper delegate) {
            super(OpenAiApi.ChatCompletionMessage.class);
            this.delegate = delegate;
        }

        @Override
        public OpenAiApi.ChatCompletionMessage deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            OpenAiApi.ChatCompletionMessage original = delegate.treeToValue(node, OpenAiApi.ChatCompletionMessage.class);
            String reasoning = original.reasoningContent();
            if (reasoning == null || reasoning.isBlank()) reasoning = reasoningFrom(node);
            return new OpenAiApi.ChatCompletionMessage(original.rawContent(), original.role(), original.name(),
                    original.toolCallId(), original.toolCalls(), original.refusal(), original.audioOutput(),
                    original.annotations(), reasoning);
        }

        private String reasoningFrom(JsonNode node) {
            for (String key : List.of("thinking", "reasoning", "reasoning_details")) {
                String value = reasoningValue(node.get(key));
                if (value != null && !value.isBlank()) return value;
            }
            return null;
        }

        private String reasoningValue(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isTextual() || node.isNumber() || node.isBoolean()) return node.asText();
            if (node.isArray()) {
                StringBuilder value = new StringBuilder();
                node.forEach(item -> {
                    String part = reasoningValue(item);
                    if (part != null && !part.isBlank()) value.append(part);
                });
                return value.isEmpty() ? null : value.toString();
            }
            for (String key : List.of("text", "content", "summary", "reasoning")) {
                String value = reasoningValue(node.get(key));
                if (value != null && !value.isBlank()) return value;
            }
            return null;
        }
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
