package com.dynorix.gaimebridge.service.impl;

import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import com.dynorix.gaimebridge.dto.DocumentDetailResponse;
import com.dynorix.gaimebridge.dto.DocumentLineResponse;
import com.dynorix.gaimebridge.dto.DocumentSearchRequest;
import com.dynorix.gaimebridge.dto.DocumentSummaryResponse;
import com.dynorix.gaimebridge.exception.ResourceNotFoundException;
import com.dynorix.gaimebridge.repository.TaxDocumentRepository;
import com.dynorix.gaimebridge.service.TaxDocumentQueryService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaxDocumentQueryServiceImpl implements TaxDocumentQueryService {

    private final TaxDocumentRepository taxDocumentRepository;

    public TaxDocumentQueryServiceImpl(TaxDocumentRepository taxDocumentRepository) {
        this.taxDocumentRepository = taxDocumentRepository;
    }

    @Override
    public Page<DocumentSummaryResponse> findDocuments(DocumentSearchRequest request, Pageable pageable) {
        return taxDocumentRepository.findAll(buildSpecification(request), pageable)
                .map(this::toSummaryResponse);
    }

    @Override
    public DocumentDetailResponse getDocument(UUID id) {
        TaxDocument document = taxDocumentRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tax document not found: " + id));
        return toDetailResponse(document);
    }

    @Override
    public DocumentDetailResponse getDocumentByNumber(String documentNumber) {
        TaxDocument document = taxDocumentRepository.findByDocumentNumberWithLines(documentNumber)
                .stream().findFirst()
                .orElseThrow(
                        () -> new ResourceNotFoundException("Tax document not found by number: " + documentNumber));
        return toDetailResponse(document);
    }

    private Specification<TaxDocument> buildSpecification(DocumentSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.documentNumber() != null && !request.documentNumber().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("documentNumber")),
                        "%" + request.documentNumber().toLowerCase() + "%"));
            }
            if (request.counterpartyTaxId() != null && !request.counterpartyTaxId().isBlank()) {
                predicates.add(cb.or(
                        cb.equal(root.get("seller").get("taxId"), request.counterpartyTaxId()),
                        cb.equal(root.get("buyer").get("taxId"), request.counterpartyTaxId())));
            }
            if (request.portalStatus() != null && !request.portalStatus().isBlank()) {
                predicates.add(cb.equal(root.get("portalStatus"), request.portalStatus()));
            }
            if (request.direction() != null) {
                predicates.add(cb.equal(root.get("direction"), request.direction()));
            }
            if (request.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("documentDate"), request.dateFrom()));
            }
            if (request.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("documentDate"), request.dateTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private DocumentSummaryResponse toSummaryResponse(TaxDocument document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getExternalDocumentId(),
                document.getDirection(),
                document.getDocumentSeries(),
                document.getDocumentNumber(),
                document.getDocumentDate(),
                document.getPortalSignedAt(),
                document.getDocumentTypeName(),
                document.getEntryTypeName(),
                document.getPortalStatus(),
                document.getSeller().getName(),
                document.getSeller().getTaxId(),
                document.getBuyer().getName(),
                document.getBuyer().getTaxId(),
                document.getVatAmount(),
                document.getTotalAmount(),
                document.getCurrencyCode(),
                document.getProcessingState());
    }

    private DocumentDetailResponse toDetailResponse(TaxDocument document) {
        List<DocumentLineResponse> lines = document.getLines().stream()
                .map(line -> new DocumentLineResponse(
                        line.getId(),
                        line.getLineNumber(),
                        line.getProductCode(),
                        line.getProductName(),
                        line.getDescription(),
                        line.getGtin(),
                        line.getUnitCode(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getLineAmount(),
                        line.getVatRate(),
                        line.getVatAmount()))
                .toList();

        return new DocumentDetailResponse(
                document.getId(),
                document.getExternalDocumentId(),
                document.getDirection(),
                document.getDocumentSeries(),
                document.getDocumentNumber(),
                document.getDocumentDate(),
                document.getPortalDetailUrl(),
                document.getPortalCreatedAt(),
                document.getPortalSignedAt(),
                document.getPortalUpdatedAt(),
                document.getDocumentTypeName(),
                document.getEntryTypeName(),
                document.getObjectName(),
                document.getDocumentVersionLabel(),
                document.getAmendmentType(),
                document.getRelatedDocumentNumber(),
                document.getPortalStatus(),
                document.getSeller().getName(),
                document.getSeller().getTaxId(),
                document.getBuyer().getName(),
                document.getBuyer().getTaxId(),
                document.getBaseNote(),
                document.getAdditionalNote(),
                document.getReasonText(),
                document.getExciseAmount(),
                document.getNetAmount(),
                document.getVatAmount(),
                document.getTaxableAmount(),
                document.getNonTaxableAmount(),
                document.getVatExemptAmount(),
                document.getZeroRatedAmount(),
                document.getRoadTaxAmount(),
                document.getAdvanceDocumentSeries(),
                document.getAdvanceDocumentNumber(),
                document.getAdvanceAmount(),
                document.getTotalAmount(),
                document.getCurrencyCode(),
                document.getProcessingState(),
                document.getDetailLoadedAt(),
                document.getLastSyncedAt(),
                lines);
    }
}
