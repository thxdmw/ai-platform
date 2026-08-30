package com.thx.aiplatform.server;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/server/v1/operations")
class ServerOperationController {

    private final ServerOperationService operationService;

    ServerOperationController(ServerOperationService operationService) { this.operationService = operationService; }

    @PostMapping("/{actionId}/approve")
    ServerOperationResult approve(@PathVariable String actionId) { return operationService.approve(actionId); }

    @DeleteMapping("/{actionId}")
    void cancel(@PathVariable String actionId) { operationService.cancel(actionId); }
}
