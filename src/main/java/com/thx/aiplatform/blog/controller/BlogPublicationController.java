package com.thx.aiplatform.blog.controller;
import com.thx.aiplatform.blog.service.BlogPublicationService;
import com.thx.aiplatform.blog.model.PublicationResult;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布确认的 REST 入口（瘦控制器）：页面上的「发布/取消」按钮落到这里的两个端点，
 * 只转发给 BlogPublicationService，不承载任何业务逻辑。
 */
@RestController
@RequestMapping("/api/blog/v1/publications")
public class BlogPublicationController {

    private final BlogPublicationService publicationService;

    public BlogPublicationController(BlogPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping("/{actionId}/approve")
    public PublicationResult approve(@PathVariable String actionId) {
        return publicationService.approve(actionId);
    }

    @DeleteMapping("/{actionId}")
    public void cancel(@PathVariable String actionId) {
        publicationService.cancel(actionId);
    }
}
