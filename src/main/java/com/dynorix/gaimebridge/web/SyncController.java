package com.dynorix.gaimebridge.web;

import com.dynorix.gaimebridge.dto.SyncJobResponse;
import com.dynorix.gaimebridge.dto.SyncRequest;
import com.dynorix.gaimebridge.service.SyncOrchestratorService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncOrchestratorService syncOrchestratorService;

    public SyncController(SyncOrchestratorService syncOrchestratorService) {
        this.syncOrchestratorService = syncOrchestratorService;
    }

    @PostMapping("/documents")
    public SyncJobResponse startSync(@Valid @RequestBody SyncRequest request, Principal principal) {
        return syncOrchestratorService.startSync(request, principal != null ? principal.getName() : "portal-ui");
    }

    @GetMapping("/jobs/{jobId}")
    public SyncJobResponse getJob(@PathVariable UUID jobId) {
        return syncOrchestratorService.getJob(jobId);
    }

    @PostMapping("/jobs/{jobId}/stop")
    public SyncJobResponse stopJob(@PathVariable UUID jobId) {
        return syncOrchestratorService.stopJob(jobId);
    }
}
