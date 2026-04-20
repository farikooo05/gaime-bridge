package com.dynorix.gaimebridge.service.impl;

import com.dynorix.gaimebridge.application.port.SyncProgressListener;
import com.dynorix.gaimebridge.application.port.TaxPortalClient;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentDetails;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentEvent;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentRelationTree;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentSummary;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentVersion;
import com.dynorix.gaimebridge.application.port.TaxPortalSyncRequest;
import com.dynorix.gaimebridge.application.port.TaxPortalSyncedDocument;
import com.dynorix.gaimebridge.domain.entity.SyncJob;
import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import com.dynorix.gaimebridge.domain.entity.TaxDocumentLine;
import com.dynorix.gaimebridge.domain.enumtype.DocumentProcessingState;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
import com.dynorix.gaimebridge.dto.SyncRequest;
import com.dynorix.gaimebridge.exception.AuthenticationRequiredException;
import com.dynorix.gaimebridge.repository.SyncJobRepository;
import com.dynorix.gaimebridge.repository.TaxDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SyncExecutionService.class);

    private final TaxPortalClient taxPortalClient;
    private final SyncJobRepository syncJobRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final ObjectMapper objectMapper;

    public SyncExecutionService(
            TaxPortalClient taxPortalClient,
            SyncJobRepository syncJobRepository,
            TaxDocumentRepository taxDocumentRepository,
            ObjectMapper objectMapper
    ) {
        this.taxPortalClient = taxPortalClient;
        this.syncJobRepository = syncJobRepository;
        this.taxDocumentRepository = taxDocumentRepository;
        this.objectMapper = objectMapper;
    }

    public com.dynorix.gaimebridge.dto.AuthStatusResponse checkPortalSession() {
        return taxPortalClient.checkSession();
    }

    @Async
    public void runSync(UUID syncJobId, SyncRequest request) {
        SyncJob syncJob = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new IllegalStateException("Sync job not found: " + syncJobId));

        syncJob.setStatus(JobStatus.RUNNING);
        syncJob.setStartedAt(OffsetDateTime.now());
        updatePhase(syncJob, SyncPhase.OPENING_LOGIN, "Opening the portal login page.");

        try {
            List<TaxPortalSyncedDocument> syncedDocuments = taxPortalClient.syncDocuments(
                    new TaxPortalSyncRequest(
                            request.portalPhone(),
                            request.portalUserId(),
                            request.direction(),
                            request.dateFrom(),
                            request.dateTo(),
                            request.loadDocumentDetails()),
                    progressListener(syncJobId),
                    () -> isCancelled(syncJobId));
            syncJob = reload(syncJobId);
            int persisted = 0;
            if (!syncedDocuments.isEmpty()) {
                updatePhase(syncJob, SyncPhase.PERSISTING_DOCUMENTS, "Saving imported documents in Dynorix.");
            }

            for (TaxPortalSyncedDocument syncedDocument : syncedDocuments) {
                // Check if job was cancelled between documents
                if (isCancelled(syncJobId)) {
                    log.info("Sync job {} cancelled by user. Stopping persistence loop.", syncJobId);
                    return;
                }

                saveSyncedDocument(syncedDocument, request.loadDocumentDetails());
                persisted++;
                
                // Throttle updates to DB to avoid write storms (update every 50 docs or at the end)
                if (persisted % 50 == 0 || persisted == syncedDocuments.size()) {
                    syncJob = reload(syncJobId);
                    syncJob.setDocumentsPersisted(persisted);
                    syncJobRepository.save(syncJob);
                }
            }

            syncJob = reload(syncJobId);
            syncJob.setDocumentsPersisted(persisted);
            syncJob.setStatus(JobStatus.COMPLETED);
            syncJob.setFinishedAt(OffsetDateTime.now());
            syncJob.setPhase(SyncPhase.COMPLETED);
            syncJob.setPhaseMessage("Documents are ready.");
            syncJobRepository.save(syncJob);
        } catch (com.dynorix.gaimebridge.exception.SyncCancelledException ex) {
            log.info("Sync job {} interrupted by user (caught in service).", syncJobId);
            syncJob = reload(syncJobId);
            syncJob.setStatus(JobStatus.CANCELLED);
            syncJob.setFinishedAt(OffsetDateTime.now());
            syncJob.setPhase(SyncPhase.CANCELLED);
            syncJob.setPhaseMessage("Sync stopped by user.");
            syncJobRepository.save(syncJob);
        } catch (AuthenticationRequiredException ex) {
            syncJob = syncJobRepository.findById(syncJobId).orElse(syncJob);
            syncJob.setStatus(JobStatus.FAILED);
            syncJob.setFinishedAt(OffsetDateTime.now());
            syncJob.setPhase(SyncPhase.FAILED);
            syncJob.setPhaseMessage("Authentication Required");
            syncJob.setErrorMessage("Please go to the Auth tab and log in to the portal first.");
            syncJobRepository.save(syncJob);
        } catch (Exception ex) {
            syncJob = syncJobRepository.findById(syncJobId).orElse(syncJob);
            syncJob.setStatus(JobStatus.FAILED);
            syncJob.setFinishedAt(OffsetDateTime.now());
            syncJob.setPhase(SyncPhase.FAILED);
            syncJob.setPhaseMessage(ex.getMessage());
            syncJob.setErrorMessage(ex.getMessage());
            syncJobRepository.save(syncJob);
        }
    }

    @Transactional
    public void saveSyncedDocument(TaxPortalSyncedDocument syncedDocument, boolean loadDocumentDetails) {
        TaxPortalDocumentSummary summary = syncedDocument.summary();
        TaxDocument document = taxDocumentRepository.findByExternalDocumentIdWithLines(summary.externalDocumentId())
                .orElseGet(TaxDocument::new);
        
        applySummary(document, summary);

        if (loadDocumentDetails && syncedDocument.details() != null) {
            applyDetails(
                    document,
                    syncedDocument.details(),
                    syncedDocument.versions(),
                    syncedDocument.history(),
                    syncedDocument.relationTree());
        }

        taxDocumentRepository.save(document);
    }

    private SyncProgressListener progressListener(UUID syncJobId) {
        return (phase, message) -> {
            SyncJob job = reload(syncJobId);
            updatePhase(job, phase, message);
        };
    }

    private void updatePhase(SyncJob syncJob, SyncPhase phase, String message) {
        syncJob.setPhase(phase);
        syncJob.setPhaseMessage(message);
        log.info("Sync job {} -> phase={} message={}", syncJob.getId(), phase, message);
        syncJobRepository.save(syncJob);
    }

    private boolean isCancelled(UUID syncJobId) {
        return syncJobRepository.findById(syncJobId)
                .map(job -> job.getStatus() == JobStatus.CANCELLED)
                .orElse(false);
    }

    private SyncJob reload(UUID syncJobId) {
        return syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new IllegalStateException("Sync job not found: " + syncJobId));
    }

    private void applySummary(TaxDocument document, TaxPortalDocumentSummary summary) {
        document.setExternalDocumentId(summary.externalDocumentId());
        document.setDirection(summary.direction());
        document.setDocumentSeries(summary.documentSeries());
        document.setDocumentNumber(summary.documentNumber());
        document.setDocumentDate(summary.documentDate());
        document.setPortalDetailUrl(summary.portalDetailUrl());
        document.setPortalCreatedAt(summary.portalCreatedAt());
        document.setPortalSignedAt(summary.portalSignedAt());
        document.setPortalUpdatedAt(summary.portalUpdatedAt());
        document.setDocumentTypeName(summary.documentTypeName());
        document.setEntryTypeName(summary.entryTypeName());
        document.setObjectName(summary.objectName());
        document.setDocumentVersionLabel(summary.documentVersionLabel());
        document.setAmendmentType(summary.amendmentType());
        document.setRelatedDocumentNumber(summary.relatedDocumentNumber());
        document.setPortalStatus(summary.portalStatus());
        document.getSeller().setName(summary.sellerName());
        document.getSeller().setTaxId(summary.sellerTaxId());
        document.getBuyer().setName(summary.buyerName());
        document.getBuyer().setTaxId(summary.buyerTaxId());
        document.setBaseNote(summary.baseNote());
        document.setAdditionalNote(summary.additionalNote());
        document.setReasonText(summary.reasonText());
        document.setExciseAmount(summary.exciseAmount());
        document.setNetAmount(summary.netAmount());
        document.setVatAmount(summary.vatAmount());
        document.setTaxableAmount(summary.taxableAmount());
        document.setNonTaxableAmount(summary.nonTaxableAmount());
        document.setVatExemptAmount(summary.vatExemptAmount());
        document.setZeroRatedAmount(summary.zeroRatedAmount());
        document.setRoadTaxAmount(summary.roadTaxAmount());
        document.setAdvanceDocumentSeries(summary.advanceDocumentSeries());
        document.setAdvanceDocumentNumber(summary.advanceDocumentNumber());
        document.setAdvanceAmount(summary.advanceAmount());
        document.setTotalAmount(summary.totalAmount());
        document.setProcessingState(DocumentProcessingState.SUMMARY_LOADED);
        document.setLastSyncedAt(OffsetDateTime.now());
        document.getOrCreateSnapshot().setSourceRowNumber(summary.sourceRowNumber());
        document.getOrCreateSnapshot().setRawPayload(summary.rawPayload());
        document.getOrCreateSnapshot().setExtractedAt(OffsetDateTime.now());
    }

    private void applyDetails(
            TaxDocument document,
            TaxPortalDocumentDetails details,
            List<TaxPortalDocumentVersion> versions,
            List<TaxPortalDocumentEvent> history,
            TaxPortalDocumentRelationTree relationTree
    ) {
        List<TaxDocumentLine> lines = details.lines().stream().map(rawLine -> {
            TaxDocumentLine line = new TaxDocumentLine();
            line.setLineNumber(rawLine.lineNumber());
            line.setProductCode(rawLine.productCode());
            line.setProductName(rawLine.productName());
            line.setDescription(rawLine.description());
            line.setGtin(rawLine.gtin());
            line.setUnitCode(rawLine.unitCode());
            line.setQuantity(rawLine.quantity());
            line.setUnitPrice(rawLine.unitPrice());
            line.setLineAmount(rawLine.lineAmount());
            line.setVatRate(rawLine.vatRate());
            line.setVatAmount(rawLine.vatAmount());
            line.setRawLinePayload(rawLine.rawPayload());
            return line;
        }).toList();
        document.replaceLines(lines);
        document.setPortalCreatedAt(details.portalCreatedAt() != null ? details.portalCreatedAt() : document.getPortalCreatedAt());
        document.setPortalUpdatedAt(details.portalUpdatedAt() != null ? details.portalUpdatedAt() : document.getPortalUpdatedAt());
        document.setObjectName(details.objectName() != null ? details.objectName() : document.getObjectName());
        document.setDocumentVersionLabel(details.documentVersionLabel() != null ? details.documentVersionLabel() : document.getDocumentVersionLabel());
        document.setAmendmentType(details.amendmentType() != null ? details.amendmentType() : document.getAmendmentType());
        document.setRelatedDocumentNumber(details.relatedDocumentNumber() != null ? details.relatedDocumentNumber() : document.getRelatedDocumentNumber());
        document.setAdvanceDocumentSeries(details.advanceDocumentSeries() != null ? details.advanceDocumentSeries() : document.getAdvanceDocumentSeries());
        document.setAdvanceDocumentNumber(details.advanceDocumentNumber() != null ? details.advanceDocumentNumber() : document.getAdvanceDocumentNumber());
        document.setAdvanceAmount(details.advanceAmount() != null ? details.advanceAmount() : document.getAdvanceAmount());
        document.setBaseNote(details.baseNote() != null ? details.baseNote() : document.getBaseNote());
        document.setAdditionalNote(details.additionalNote() != null ? details.additionalNote() : document.getAdditionalNote());
        document.setExciseAmount(details.exciseAmount() != null ? details.exciseAmount() : document.getExciseAmount());
        document.setNetAmount(details.netAmount() != null ? details.netAmount() : document.getNetAmount());
        document.setVatAmount(details.vatAmount() != null ? details.vatAmount() : document.getVatAmount());
        document.setTaxableAmount(details.taxableAmount() != null ? details.taxableAmount() : document.getTaxableAmount());
        document.setNonTaxableAmount(details.nonTaxableAmount() != null ? details.nonTaxableAmount() : document.getNonTaxableAmount());
        document.setVatExemptAmount(details.vatExemptAmount() != null ? details.vatExemptAmount() : document.getVatExemptAmount());
        document.setZeroRatedAmount(details.zeroRatedAmount() != null ? details.zeroRatedAmount() : document.getZeroRatedAmount());
        document.setRoadTaxAmount(details.roadTaxAmount() != null ? details.roadTaxAmount() : document.getRoadTaxAmount());
        document.setTotalAmount(details.totalAmount() != null ? details.totalAmount() : document.getTotalAmount());
        document.getOrCreateSnapshot().setParsedPayload(buildParsedPayloadEnvelope(details, versions, history, relationTree));
        document.getOrCreateSnapshot().setHtmlSnapshotPath(details.htmlSnapshotPath());
        document.getOrCreateSnapshot().setParserVersion("json-first-v2");
        document.getOrCreateSnapshot().setExtractedAt(OffsetDateTime.now());
        document.setDetailLoadedAt(OffsetDateTime.now());
        document.setProcessingState(DocumentProcessingState.DETAILS_LOADED);
    }

    private String buildParsedPayloadEnvelope(
            TaxPortalDocumentDetails details,
            List<TaxPortalDocumentVersion> versions,
            List<TaxPortalDocumentEvent> history,
            TaxPortalDocumentRelationTree relationTree
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("detail", readTreeOrNull(details.parsedPayload()));

        ArrayNode versionsNode = objectMapper.createArrayNode();
        versions.forEach(version -> versionsNode.add(readTreeOrObject(version.rawPayload())));
        root.set("versions", versionsNode);

        ArrayNode historyNode = objectMapper.createArrayNode();
        history.forEach(event -> historyNode.add(readTreeOrObject(event.rawPayload())));
        root.set("history", historyNode);

        root.set("tree", readTreeOrObject(relationTree == null ? null : relationTree.treePayload()));
        root.set("parentInvoiceHistories", readTreeOrObject(relationTree == null ? null : relationTree.parentHistoriesPayload()));
        return root.toString();
    }

    private com.fasterxml.jackson.databind.JsonNode readTreeOrNull(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to build parsed payload envelope from tax portal JSON", ex);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode readTreeOrObject(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to build parsed payload envelope from tax portal JSON", ex);
        }
    }
}
