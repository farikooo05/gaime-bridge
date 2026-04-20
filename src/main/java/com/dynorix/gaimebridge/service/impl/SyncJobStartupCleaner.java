package com.dynorix.gaimebridge.service.impl;

import com.dynorix.gaimebridge.domain.entity.SyncJob;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.repository.SyncJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SyncJobStartupCleaner {

    private static final Logger log = LoggerFactory.getLogger(SyncJobStartupCleaner.class);

    private final SyncJobRepository syncJobRepository;

    public SyncJobStartupCleaner(SyncJobRepository syncJobRepository) {
        this.syncJobRepository = syncJobRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupStuckJobs() {
        log.info("Checking for stuck sync jobs needing cleanup...");

        List<SyncJob> stuckJobs = syncJobRepository.findByStatusIn(List.of(JobStatus.RUNNING, JobStatus.QUEUED));

        if (stuckJobs.isEmpty()) {
            return;
        }

        log.warn("Found {} stuck sync jobs. Marking them as FAILED.", stuckJobs.size());

        for (SyncJob job : stuckJobs) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(OffsetDateTime.now());
            job.setPhaseMessage("Job interrupted by system restart.");
            job.setErrorMessage("The application was likely restarted while this job was active.");
            syncJobRepository.save(job);
            log.info("Marked job {} as FAILED.", job.getId());
        }
    }
}
