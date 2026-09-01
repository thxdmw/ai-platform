package com.thx.aiplatform.platform;

/**
 * 一次对话选中的 OpenAI 兼容模型连接。密钥只在服务端内存中短暂存在，不能写入日志或返回页面。
 */
public record AssistantModelConnection(
        String baseUrl,
        String chatCompletionsPath,
        String apiProtocol,
        String apiKey,
        String model,
        String reasoningEffort
) {
    @Override
    public String toString() {
        return "AssistantModelConnection[baseUrl=" + baseUrl + ", chatCompletionsPath=" + chatCompletionsPath
                + ", apiProtocol=" + apiProtocol + ", apiKey=***, model=" + model
                + ", reasoningEffort=" + reasoningEffort + "]";
    }
}
