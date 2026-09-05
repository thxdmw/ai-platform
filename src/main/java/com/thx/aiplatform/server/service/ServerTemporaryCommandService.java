package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.entity.ServerEntity;

/**
 * ReAct 临时命令入口接口：命令不需要预先保存，服务端能证明只读时立即执行，其他情况
 * 只创建待审批动作。
 */
public interface ServerTemporaryCommandService {

    /** 返回给模型的工具结果只包含执行事实或暂停状态，不泄露内部 actionId。 */
    String executeOrPrepare(String conversationId, ServerEntity server, String commandText,
                            String workingDirectory, String reason);
}