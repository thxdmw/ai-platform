package com.thx.aiplatform.website;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
