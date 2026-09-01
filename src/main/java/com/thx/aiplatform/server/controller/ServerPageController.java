package com.thx.aiplatform.server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 服务器助手页面的路由：/server 与 /server/assistant 都重定向到带尾斜杠的规范路径，
 * 否则静态资源的相对引用（如 assets/…）会被解析到错误的父级路径上。
 */
@Controller
class ServerPageController {

    @GetMapping({"/server", "/server/assistant"})
    String redirect() { return "redirect:/server/assistant/"; }

    @GetMapping("/server/assistant/")
    String index() { return "forward:/server/assistant/index.html"; }
}
