package com.thx.aiplatform.server.controller;

import com.thx.aiplatform.server.dto.ServerModelProviderRequest;
import com.thx.aiplatform.server.dto.ServerModelProviderProbeRequest;
import com.thx.aiplatform.server.vo.ServerModelProviderProbeResult;
import com.thx.aiplatform.server.vo.ServerModelProviderView;
import com.thx.aiplatform.server.vo.ServerModelView;
import com.thx.aiplatform.server.service.ServerModelProviderService;
import com.thx.aiplatform.server.service.ServerModelProviderProbeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/server/v1/model-providers")
class ServerModelProviderController {

    private final ServerModelProviderService service;
    private final ServerModelProviderProbeService probeService;

    ServerModelProviderController(ServerModelProviderService service,
                                  ServerModelProviderProbeService probeService) {
        this.service = service;
        this.probeService = probeService;
    }

    @GetMapping
    List<ServerModelProviderView> list() { return service.listProviders(); }

    @GetMapping("/models")
    List<ServerModelView> models() { return service.listEnabledModels(); }

    @PostMapping("/probe")
    ServerModelProviderProbeResult probe(@Valid @RequestBody ServerModelProviderProbeRequest request) {
        return probeService.probe(request);
    }

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
