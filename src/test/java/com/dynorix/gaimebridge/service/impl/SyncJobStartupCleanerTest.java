package com.dynorix.gaimebridge.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dynorix.gaimebridge.domain.entity.SyncJob;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.repository.SyncJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncJobStartupCleanerTest {

    @Mock
    private SyncJobRepository syncJobRepository;

    @InjectMocks
    private SyncJobStartupCleaner syncJobStartupCleaner;

    @Test
    void shouldMarkStuckJobsAsFailedOnCleanup() {
        // Given
        SyncJob runningJob = new SyncJob();
        runningJob.setStatus(JobStatus.RUNNING);
        
        SyncJob queuedJob = new SyncJob();
        queuedJob.setStatus(JobStatus.QUEUED);

        when(syncJobRepository.findByStatusIn(any())).thenReturn(List.of(runningJob, queuedJob));

        // When
        syncJobStartupCleaner.cleanupStuckJobs();

        // Then
        ArgumentCaptor<SyncJob> captor = ArgumentCaptor.forClass(SyncJob.class);
        verify(syncJobRepository, times(2)).save(captor.capture());

        List<SyncJob> savedJobs = captor.getAllValues();
        assertThat(savedJobs).allMatch(job -> job.getStatus() == JobStatus.FAILED);
        assertThat(savedJobs).allMatch(job -> job.getPhaseMessage().contains("restart"));
    }

    @Test
    void shouldDoNothingWhenNoStuckJobsFound() {
        // Given
        when(syncJobRepository.findByStatusIn(any())).thenReturn(List.of());

        // When
        syncJobStartupCleaner.cleanupStuckJobs();

        // Then
        verify(syncJobRepository, never()).save(any());
    }
}
