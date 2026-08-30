package com.thx.aiplatform.blog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blog/v1/auth")
class BlogAuthController {

    @PostMapping("/verify")
    ResponseEntity<Void> verify() {
        return ResponseEntity.noContent().build();
    }
}
