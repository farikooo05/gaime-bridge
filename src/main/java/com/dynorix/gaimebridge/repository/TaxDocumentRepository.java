package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxDocumentRepository extends JpaRepository<TaxDocument, UUID>, JpaSpecificationExecutor<TaxDocument> {

    @Query("SELECT d FROM TaxDocument d LEFT JOIN FETCH d.lines WHERE d.externalDocumentId = :externalDocumentId")
    Optional<TaxDocument> findByExternalDocumentIdWithLines(@Param("externalDocumentId") String externalDocumentId);

    Optional<TaxDocument> findFirstByDocumentNumberOrderByDocumentDateDesc(String documentNumber);

    @Query("SELECT d FROM TaxDocument d LEFT JOIN FETCH d.lines WHERE d.id = :id")
    Optional<TaxDocument> findByIdWithLines(@Param("id") UUID id);

    @Query("SELECT d FROM TaxDocument d LEFT JOIN FETCH d.lines WHERE d.documentNumber = :documentNumber ORDER BY d.documentDate DESC")
    List<TaxDocument> findByDocumentNumberWithLines(@Param("documentNumber") String documentNumber);

    @Query("SELECT d FROM TaxDocument d LEFT JOIN FETCH d.lines WHERE d.id IN :ids")
    List<TaxDocument> findAllByIdWithLines(@Param("ids") Set<UUID> ids);
}
