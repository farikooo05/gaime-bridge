package com.dynorix.gaimebridge.service;

import com.dynorix.gaimebridge.dto.SyncJobResponse;
import com.dynorix.gaimebridge.dto.SyncRequest;
import java.util.UUID;

public interface SyncOrchestratorService {

    SyncJobResponse startSync(SyncRequest request, String initiatedBy);

    SyncJobResponse getJob(UUID jobId);

    SyncJobResponse stopJob(UUID jobId);

    com.dynorix.gaimebridge.dto.AuthStatusResponse checkPortalSession();
}
