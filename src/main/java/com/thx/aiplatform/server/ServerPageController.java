package com.thx.aiplatform.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ServerPageController {

    @GetMapping({"/server", "/server/assistant"})
    String redirect() { return "redirect:/server/assistant/"; }

    @GetMapping("/server/assistant/")
    String index() { return "forward:/server/assistant/index.html"; }
}
