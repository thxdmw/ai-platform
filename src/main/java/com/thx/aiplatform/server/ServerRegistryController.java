package com.thx.aiplatform.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/server/v1/servers")
class ServerRegistryController {

    private final ServerRegistry registry;

    ServerRegistryController(ServerRegistry registry) { this.registry = registry; }

    @GetMapping
    List<ServerView> list() { return registry.views(); }
}
