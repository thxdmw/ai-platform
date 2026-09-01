package com.thx.aiplatform.website.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 网站公开资料的唯一来源：启动时从 classpath 加载，内容整体注入网站助手的系统提示词，
 * 作为「只许按资料回答、不得编造」的约束依据。
 * <p>因此加载失败必须让应用直接启动失败（见构造器），不允许带着缺失的知识库带病运行——
 * 那等于让模型在一个没有约束的起点上自由发挥。</p>
 */
@Component
public class WebsiteKnowledge {

    private static final String KNOWLEDGE_LOCATION = "classpath:/knowledge/website.md";

    private final String content;

    public WebsiteKnowledge(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(KNOWLEDGE_LOCATION);
        try (var inputStream = resource.getInputStream()) {
            this.content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // 网站助手不能在缺少公开知识时带病启动，否则模型容易编造站点信息。
            throw new IllegalStateException("无法加载网站助手知识：" + KNOWLEDGE_LOCATION, exception);
        }
    }

    public String content() {
        return content;
    }
}
