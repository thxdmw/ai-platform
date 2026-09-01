package com.thx.aiplatform.server.controller;

import com.thx.aiplatform.server.model.ServerModelProviderRequest;
import com.thx.aiplatform.server.model.ServerModelProviderView;
import com.thx.aiplatform.server.model.ServerModelView;
import com.thx.aiplatform.server.service.ServerModelProviderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/server/v1/model-providers")
class ServerModelProviderController {

    private final ServerModelProviderService service;

    ServerModelProviderController(ServerModelProviderService service) { this.service = service; }

    @GetMapping
    List<ServerModelProviderView> list() { return service.listProviders(); }

    @GetMapping("/models")
    List<ServerModelView> models() { return service.listEnabledModels(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServerModelProviderView create(@Valid @RequestBody ServerModelProviderRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    ServerModelProviderView update(@PathVariable String id, @Valid @RequestBody ServerModelProviderRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { service.delete(id); }
}
