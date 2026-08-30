package com.thx.aiplatform.blog;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blog/v1/publications")
public class BlogPublicationController {

    private final BlogPublicationService publicationService;

    public BlogPublicationController(BlogPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping("/{actionId}/approve")
    public PublicationResult approve(@PathVariable String actionId) {
        return publicationService.approve(actionId);
    }

    @DeleteMapping("/{actionId}")
    public void cancel(@PathVariable String actionId) {
        publicationService.cancel(actionId);
    }
}
