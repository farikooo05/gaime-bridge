package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.ExportJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
}
