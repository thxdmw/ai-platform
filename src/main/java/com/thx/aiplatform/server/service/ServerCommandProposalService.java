package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.vo.PendingServerCommandProposalView;
import com.thx.aiplatform.server.vo.ServerCommandProposalResult;

/**
 * 「提议添加命令」服务接口：模型经 proposeCommand 工具提出一条命令 → 服务端生成一次性
 * 确认选项并自动分级风险 → 用户点击添加 → 服务端把命令落库并签发续跑凭证。
 */
public interface ServerCommandProposalService {

    PendingServerCommandProposalView prepare(String conversationId, ServerEntity server, String name,
                                             String description, String commandText, String parameterSchema,
                                             String reason);

    ServerCommandProposalResult approve(String actionId);

    void cancel(String actionId);

    void cancelForConversation(String conversationId);

    /** 供 SSE 流结束时查询：把该对话尚未处理的提议作为 action 事件推给页面展示。 */
    PendingServerCommandProposalView findForConversation(String conversationId);
}