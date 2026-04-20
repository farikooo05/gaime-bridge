package com.dynorix.gaimebridge.service.impl;

import com.dynorix.gaimebridge.domain.entity.SyncJob;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
import com.dynorix.gaimebridge.dto.SyncJobResponse;
import com.dynorix.gaimebridge.dto.SyncRequest;
import com.dynorix.gaimebridge.exception.ResourceNotFoundException;
import com.dynorix.gaimebridge.repository.SyncJobRepository;
import com.dynorix.gaimebridge.service.SyncOrchestratorService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncOrchestratorServiceImpl implements SyncOrchestratorService {

    private final SyncJobRepository syncJobRepository;
    private final SyncExecutionService syncExecutionService;

    public SyncOrchestratorServiceImpl(
            SyncJobRepository syncJobRepository,
            SyncExecutionService syncExecutionService
    ) {
        this.syncJobRepository = syncJobRepository;
        this.syncExecutionService = syncExecutionService;
    }

    @Override
    @Transactional
    public synchronized SyncJobResponse startSync(SyncRequest request, String initiatedBy) {
        // Prevent concurrent sync jobs
        var activeJob = syncJobRepository.findFirstByStatusInOrderByCreatedAtDesc(List.of(JobStatus.RUNNING, JobStatus.QUEUED));
        
        if (activeJob.isPresent()) {
            throw new IllegalStateException("Another sync job is already active: " + activeJob.get().getId());
        }

        // If this was just a check for active jobs, we're done (no job is currently active)
        if (Boolean.TRUE.equals(request.dryRun())) {
            return null;
        }

        SyncJob syncJob = new SyncJob();
        syncJob.setStatus(JobStatus.RUNNING);
        syncJob.setRequestedBy(initiatedBy);
        syncJob.setStartedAt(OffsetDateTime.now());
        syncJob.setPhase(SyncPhase.QUEUED);
        syncJob.setPhaseMessage("Sync request accepted. Preparing portal session.");
        String directionLabel = request.direction() == null ? "BOTH" : request.direction().name();
        syncJob.setFiltersJson("""
                {"portalPhone":"%s","direction":"%s","dateFrom":"%s","dateTo":"%s","loadDocumentDetails":%s}
                """.formatted(request.portalPhone(), directionLabel, request.dateFrom(), request.dateTo(), request.loadDocumentDetails()).trim());
        SyncJob saved = syncJobRepository.saveAndFlush(syncJob);
        scheduleAsyncSync(saved.getId(), request);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncJobResponse getJob(UUID jobId) {
        return syncJobRepository.findById(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Sync job not found: " + jobId));
    }

    @Override
    @Transactional
    public SyncJobResponse stopJob(UUID jobId) {
        SyncJob syncJob = syncJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Sync job not found: " + jobId));

        if (syncJob.getStatus() == JobStatus.RUNNING || syncJob.getStatus() == JobStatus.QUEUED) {
            syncJob.setStatus(JobStatus.CANCELLED);
            syncJob.setFinishedAt(OffsetDateTime.now());
            syncJob.setPhaseMessage("Sync stopped by user.");
            syncJobRepository.save(syncJob);
        }

        return toResponse(syncJob);
    }

    private SyncJobResponse toResponse(SyncJob syncJob) {
        return new SyncJobResponse(
                syncJob.getId(),
                syncJob.getStatus(),
                syncJob.getPhase(),
                syncJob.getPhaseMessage(),
                syncJob.getDocumentsDiscovered(),
                syncJob.getDocumentsPersisted(),
                syncJob.getStartedAt(),
                syncJob.getFinishedAt(),
                syncJob.getErrorMessage());
    }

    @Override
    public com.dynorix.gaimebridge.dto.AuthStatusResponse checkPortalSession() {
        return syncExecutionService.checkPortalSession();
    }

    private void scheduleAsyncSync(UUID syncJobId, SyncRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncExecutionService.runSync(syncJobId, request);
                }
            });
            return;
        }
        syncExecutionService.runSync(syncJobId, request);
    }
}
