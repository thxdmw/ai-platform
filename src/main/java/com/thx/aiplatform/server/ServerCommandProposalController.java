package com.thx.aiplatform.server;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

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
