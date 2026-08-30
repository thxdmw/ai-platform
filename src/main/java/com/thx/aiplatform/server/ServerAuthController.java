package com.thx.aiplatform.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/server/v1/auth")
class ServerAuthController {
    @PostMapping("/verify")
    ResponseEntity<Void> verify() { return ResponseEntity.noContent().build(); }
}
