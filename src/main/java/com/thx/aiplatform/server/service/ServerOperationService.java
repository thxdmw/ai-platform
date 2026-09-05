package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.dto.ServerOperationDecisionRequest;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.vo.PendingServerOperationView;
import com.thx.aiplatform.server.vo.ServerOperationDecisionResult;
import com.thx.aiplatform.server.vo.ServerOperationResult;

/**
 * 危险命令二次确认服务接口：模型工具只生成「待确认操作」，用户点击确认后由 approve
 * 真正执行。确认选项只存内存，且只受理 DANGEROUS 级别的命令。
 */
public interface ServerOperationService {

    PendingServerOperationView prepare(String conversationId, ServerEntity server,
                                       ServerCommandEntity command, String reason);

    PendingServerOperationView prepare(String conversationId, ServerEntity server,
                                       ServerCommandEntity command, String renderedCommand,
                                       String reason);

    /** 临时命令只在无法证明只读时进入这里，供临时命令服务调用。 */
    PendingServerOperationView prepareTemporary(String conversationId, ServerEntity server,
                                                String workingDirectory, String commandText,
                                                String renderedCommand, String reason);

    ServerOperationResult approve(String actionId);

    ServerOperationDecisionResult decide(String actionId, ServerOperationDecisionRequest request);

    boolean isTrustedExact(String conversationId, String serverId, String workingDirectory, String commandText);

    void cancel(String actionId);

    void cancelForConversation(String conversationId);

    /** 删除对话时连同本对话的精确放行记录一并清理；新消息只取消旧卡片，不清放行记录。 */
    void forgetConversation(String conversationId);

    /** 服务器配置变更或删除时调用：连接参数已变，残存的待确认操作必须作废。 */
    void cancelForServer(String serverId);

    /** 供 SSE 流结束时查询该对话的待确认操作，由控制器推给页面展示确认按钮。 */
    PendingServerOperationView findForConversation(String conversationId);
}