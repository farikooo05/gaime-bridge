package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaxDocumentRepository extends JpaRepository<TaxDocument, UUID>, JpaSpecificationExecutor<TaxDocument> {

    Optional<TaxDocument> findByExternalDocumentId(String externalDocumentId);

    Optional<TaxDocument> findFirstByDocumentNumberOrderByDocumentDateDesc(String documentNumber);
}
