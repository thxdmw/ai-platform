package com.thx.aiplatform.website.service;

import com.thx.aiplatform.website.dto.WebsiteChatRequest;
import reactor.core.publisher.Flux;

/**
 * 网站助手业务层接口：公开接口的第二层安全（固定助手编号）与知识注入在实现中落地。
 */
public interface WebsiteAssistantService {

    String ASSISTANT_ID = "website";

    Flux<String> stream(WebsiteChatRequest request);

    /** 释放网站助手指定会话的服务端模型记忆。 */
    void clear(String conversationId);
}