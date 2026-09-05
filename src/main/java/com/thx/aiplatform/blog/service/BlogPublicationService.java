package com.thx.aiplatform.blog.service;

import com.thx.aiplatform.blog.dto.BlogPublicationRequest;
import com.thx.aiplatform.blog.vo.PendingPublicationView;
import com.thx.aiplatform.blog.vo.PublicationResult;

/**
 * 发布候选（PendingPublication）的生命周期管理接口：prepare 生成带 TTL 的候选并绑定
 * 会话，approve 先原子移除再调上游，保证同一候选至多发布一次。
 */
public interface BlogPublicationService {

    PendingPublicationView prepare(String conversationId, BlogPublicationRequest rawRequest);

    PublicationResult approve(String actionId);

    PendingPublicationView findForConversation(String conversationId);

    void cancel(String actionId);

    void cancelForConversation(String conversationId);
}