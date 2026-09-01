package com.thx.aiplatform.blog.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 博客助手页面的路由薄层：无尾斜杠的旧路径重定向到规范斜杠路径，再 forward 到静态 index.html。
 * 用 forward 而非重定向是为了保持浏览器地址不变，页面里的相对资源引用才不会失效。
 */
@Controller
class BlogPageController {

    @GetMapping({"/blog", "/blog/assistant"})
    String blogAssistant() {
        return "redirect:/blog/assistant/";
    }

    @GetMapping("/blog/assistant/")
    String blogAssistantIndex() {
        return "forward:/blog/assistant/index.html";
    }
}
