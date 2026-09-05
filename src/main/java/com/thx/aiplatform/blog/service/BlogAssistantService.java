package com.thx.aiplatform.blog.service;

import com.thx.aiplatform.blog.dto.BlogChatRequest;
import reactor.core.publisher.Flux;

/**
 * 博客助手会话编排接口：固定助手身份与系统提示词，装配只读查询与发布候选两套工具。
 */
public interface BlogAssistantService {

    String ASSISTANT_ID = "blog-admin";

    Flux<String> stream(BlogChatRequest request);
}