package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.SyncJob;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    Optional<SyncJob> findFirstByStatusInOrderByCreatedAtDesc(List<JobStatus> statuses);
    
    List<SyncJob> findByStatusIn(List<JobStatus> statuses);
}
