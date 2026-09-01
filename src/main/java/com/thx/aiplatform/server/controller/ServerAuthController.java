package com.thx.aiplatform.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 口令验证端点：页面保存口令后调用 /verify 探活（请求会先经过统一拦截器完成鉴权），
 * 返回任意成功状态即表示口令可用。这个接口让前端无需持有业务数据就能确认「已就绪」。
 */
@RestController
@RequestMapping("/api/server/v1/auth")
class ServerAuthController {
    @PostMapping("/verify")
    ResponseEntity<Void> verify() { return ResponseEntity.noContent().build(); }
}
