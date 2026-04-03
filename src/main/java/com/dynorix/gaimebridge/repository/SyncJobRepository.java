package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.SyncJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {
}
