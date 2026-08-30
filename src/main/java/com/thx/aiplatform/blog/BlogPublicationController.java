package com.thx.aiplatform.blog;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blog/v1/publications")
public class BlogPublicationController {

    private final BlogPublicationService publicationService;

    public BlogPublicationController(BlogPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping
    public ResponseEntity<PendingPublicationView> prepare(@Valid @RequestBody BlogPublicationRequest request) {
        return ResponseEntity.accepted().body(publicationService.prepare(request));
    }

    @PostMapping("/{actionId}/approve")
    public PublicationResult approve(
            @PathVariable String actionId,
            @Valid @RequestBody BlogApprovalRequest request
    ) {
        return publicationService.approve(actionId, request.confirmation());
    }
}
