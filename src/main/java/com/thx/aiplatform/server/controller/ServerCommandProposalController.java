package com.thx.aiplatform.server.controller;
import com.thx.aiplatform.server.service.ServerCommandProposalService;
import com.thx.aiplatform.server.model.ServerCommandProposalResult;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 命令添加提议的确认/取消端点：页面点击「添加」调 approve，点击取消调 DELETE。
 * 瘦控制器，逻辑都在 {@link ServerCommandProposalService}。
 */
@RestController
@RequestMapping("/api/server/v1/command-proposals")
class ServerCommandProposalController {

    private final ServerCommandProposalService proposalService;

    ServerCommandProposalController(ServerCommandProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @PostMapping("/{actionId}/approve")
    ServerCommandProposalResult approve(@PathVariable String actionId) {
        return proposalService.approve(actionId);
    }

    @DeleteMapping("/{actionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String actionId) { proposalService.cancel(actionId); }
}
