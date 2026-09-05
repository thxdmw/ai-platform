package com.thx.aiplatform.website.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 网站助手后台页面的规范路由。 */
@Controller
class WebsitePageController {

    @GetMapping({"/website", "/website/assistant"})
    String redirect() {
        return "redirect:/website/assistant/";
    }

    @GetMapping("/website/assistant/")
    String index() {
        return "forward:/website/assistant/index.html";
    }
}
