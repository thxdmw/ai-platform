package com.thx.aiplatform.server.model;

import java.util.Arrays;

/** 自定义模型提供方对外暴露的 HTTP 协议。 */
public enum ServerModelApiProtocol {

    OPENAI_COMPLETIONS("openai-completions", "/v1/chat/completions"),
    OPENAI_RESPONSES("openai-responses", "/v1/responses"),
    ANTHROPIC_MESSAGES("anthropic-messages", "/v1/messages");

    private final String code;
    private final String chatPath;

    ServerModelApiProtocol(String code, String chatPath) {
        this.code = code;
        this.chatPath = chatPath;
    }

    public String code() { return code; }
    public String chatPath() { return chatPath; }

    public static ServerModelApiProtocol fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的 API 协议：" + code));
    }
}
