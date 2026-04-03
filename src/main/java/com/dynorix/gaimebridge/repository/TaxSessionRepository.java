package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.TaxSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxSessionRepository extends JpaRepository<TaxSession, UUID> {
}
