package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.IntegrationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {
}
