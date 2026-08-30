package com.thx.aiplatform.blog;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
