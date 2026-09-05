package com.thx.aiplatform.server.controller;
import com.thx.aiplatform.server.service.ServerOperationService;
import com.thx.aiplatform.server.vo.ServerOperationResult;
import com.thx.aiplatform.server.dto.ServerOperationDecisionRequest;
import com.thx.aiplatform.server.vo.ServerOperationDecisionResult;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 危险命令二次确认的控制面端点：approve 是真正触发远程执行的入口，因此这里保持极薄，
 * 不掺任何业务判断，只转发给 {@link ServerOperationService}。
 */
@RestController
@RequestMapping("/api/server/v1/operations")
class ServerOperationController {

    private final ServerOperationService operationService;

    ServerOperationController(ServerOperationService operationService) { this.operationService = operationService; }

    @PostMapping("/{actionId}/approve")
    ServerOperationResult approve(@PathVariable String actionId) { return operationService.approve(actionId); }

    @PostMapping("/{actionId}/decide")
    ServerOperationDecisionResult decide(@PathVariable String actionId,
                                         @Valid @RequestBody ServerOperationDecisionRequest request) {
        return operationService.decide(actionId, request);
    }

    @DeleteMapping("/{actionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String actionId) { operationService.cancel(actionId); }
}
