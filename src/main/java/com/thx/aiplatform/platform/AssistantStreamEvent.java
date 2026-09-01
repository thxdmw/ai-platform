package com.thx.aiplatform.platform;

/** 模型流中可安全展示的事件；reasoning 只承载提供方接口明确返回的内容。 */
public record AssistantStreamEvent(Type type, String content) {

    public enum Type { CONTENT, REASONING }

    public static AssistantStreamEvent content(String value) {
        return new AssistantStreamEvent(Type.CONTENT, value);
    }

    public static AssistantStreamEvent reasoning(String value) {
        return new AssistantStreamEvent(Type.REASONING, value);
    }
}
