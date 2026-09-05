package com.thx.aiplatform.server.service;

/**
 * 对话「续跑」凭证服务接口：模型一轮任务被打断后（危险命令已确认执行、命令已确认添加），
 * 服务端把「系统可信事件」消息暂存并签发一次性票据，用户点击续跑时服务端校验票据、
 * 取回消息交还给模型继续原任务。为什么消息必须由服务端保存而不是让模型自己携带：模型的
 * 记忆与输出都不可信，只有服务端签发的可信上下文才能约束「继续完成原任务、不得自动重试」
 * 等行为。
 */
public interface ServerActionContinuationService {

    /** 签发票据。先作废同一对话的旧票据：防止旧票据在过期前被消费，把上一轮的过时上下文带进新一轮。 */
    String prepare(String conversationId, String serverId, String message);

    /**
     * 消费票据。必须用带值比较的原子 remove 而不是「先 get 再 remove」：票据语义是
     * 只消费一次；同时校验会话与服务器绑定，防止票据被另一个会话盗用。
     */
    String consume(String id, String conversationId, String serverId);

    void cancelForConversation(String conversationId);
}