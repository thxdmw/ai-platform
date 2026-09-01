package com.thx.aiplatform.blog.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面加载时的口令自检入口：校验本身发生在 BlogAccessInterceptor，
 * 这里只需一个会触发拦截器的空端点，让前端凭 200/401/503 决定是否展示登录框。
 */
@RestController
@RequestMapping("/api/blog/v1/auth")
class BlogAuthController {

    @PostMapping("/verify")
    ResponseEntity<Void> verify() {
        return ResponseEntity.noContent().build();
    }
}
