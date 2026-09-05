package com.thx.aiplatform.server.service;

import com.thx.aiplatform.platform.AssistantStreamEvent;
import com.thx.aiplatform.server.dto.ServerChatRequest;
import com.thx.aiplatform.server.dto.ServerContinuationRequest;
import reactor.core.publisher.Flux;

/**
 * 服务器助手编排服务接口：把页面当前选中的服务器、会话与平台聊天网关组装起来，
 * 构造只属于本次对话的工具集并向模型发起流式对话。服务器身份由页面和服务端固定。
 */
public interface ServerAssistantService {

    String ASSISTANT_ID = "server-ops";

    /** 新一轮用户消息入口：先取消该对话残留的所有待确认操作/命令提议/续跑凭证。 */
    Flux<AssistantStreamEvent> stream(ServerChatRequest request);

    /** 页面动作完成后的续跑入口：消费服务端签发的续跑凭证恢复模型会话。 */
    Flux<AssistantStreamEvent> continueAfterAction(ServerContinuationRequest request);

    /** 删除对话：先清待确认操作、提议、续跑凭证与会话绑定，最后清平台侧的模型记忆。 */
    void deleteConversation(String conversationId);
}