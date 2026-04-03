package com.dynorix.gaimebridge.service.impl;

import com.dynorix.gaimebridge.config.ExportStorageProperties;
import com.dynorix.gaimebridge.domain.entity.ExportJob;
import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import com.dynorix.gaimebridge.domain.enumtype.ExportFormat;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.dto.ExportDocumentLinePayload;
import com.dynorix.gaimebridge.dto.ExportDocumentPayload;
import com.dynorix.gaimebridge.dto.ExportJobResponse;
import com.dynorix.gaimebridge.dto.ExportRequest;
import com.dynorix.gaimebridge.dto.JsonExportPayload;
import com.dynorix.gaimebridge.exception.ResourceNotFoundException;
import com.dynorix.gaimebridge.repository.ExportJobRepository;
import com.dynorix.gaimebridge.repository.TaxDocumentRepository;
import com.dynorix.gaimebridge.service.ExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportServiceImpl implements ExportService {

    private final ExportJobRepository exportJobRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final ExportStorageProperties exportStorageProperties;
    private final ObjectMapper objectMapper;

    public ExportServiceImpl(
            ExportJobRepository exportJobRepository,
            TaxDocumentRepository taxDocumentRepository,
            ExportStorageProperties exportStorageProperties,
            ObjectMapper objectMapper
    ) {
        this.exportJobRepository = exportJobRepository;
        this.taxDocumentRepository = taxDocumentRepository;
        this.exportStorageProperties = exportStorageProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ExportJobResponse createExport(ExportRequest request, String initiatedBy) {
        LinkedHashSet<UUID> requestedDocumentIds = new LinkedHashSet<>(request.documentIds());
        if (requestedDocumentIds.size() != request.documentIds().size()) {
            throw new IllegalArgumentException("Duplicate document IDs are not allowed in one export request");
        }

        ExportJob job = new ExportJob();
        job.setStatus(JobStatus.RUNNING);
        job.setRequestedBy(initiatedBy);
        job.setOutputFormat(request.format());
        job.setDocumentCount(requestedDocumentIds.size());
        job.setFiltersJson("{\"documentIdsCount\":%d}".formatted(requestedDocumentIds.size()));
        job.setStartedAt(OffsetDateTime.now());
        exportJobRepository.save(job);

        try {
            if (request.format() != ExportFormat.JSON) {
                throw new IllegalArgumentException("Only JSON export is implemented right now");
            }

            List<TaxDocument> documents = taxDocumentRepository.findAllById(requestedDocumentIds).stream()
                    .sorted(Comparator.comparing(TaxDocument::getDocumentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TaxDocument::getDocumentNumber, Comparator.nullsLast(String::compareTo)))
                    .toList();

            if (documents.size() != requestedDocumentIds.size()) {
                job.setStatus(JobStatus.FAILED);
                job.setFinishedAt(OffsetDateTime.now());
                job.setErrorMessage("One or more documents were not found for export");
                return toResponse(exportJobRepository.save(job));
            }

            Path exportPath = writeJsonExport(job.getId(), initiatedBy, documents);
            job.setOutputPath(exportPath.toAbsolutePath().toString());
            job.setStatus(JobStatus.COMPLETED);
            job.setFinishedAt(OffsetDateTime.now());
            return toResponse(exportJobRepository.save(job));
        } catch (IOException | IllegalArgumentException ex) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMessage(ex.getMessage());
            return toResponse(exportJobRepository.save(job));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExportJobResponse getJob(UUID jobId) {
        return exportJobRepository.findById(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public Path resolveExportFile(UUID jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + jobId));
        if (job.getOutputPath() == null || job.getOutputPath().isBlank()) {
            throw new ResourceNotFoundException("Export file is not ready for job: " + jobId);
        }
        Path exportPath = Path.of(job.getOutputPath());
        if (!Files.exists(exportPath)) {
            throw new ResourceNotFoundException("Export file not found on disk for job: " + jobId);
        }
        return exportPath;
    }

    private Path writeJsonExport(UUID jobId, String initiatedBy, List<TaxDocument> documents) throws IOException {
        Path baseDir = Path.of(exportStorageProperties.baseDir()).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);

        Path tempPath = baseDir.resolve("export-" + jobId + ".tmp");
        Path finalPath = baseDir.resolve("export-" + jobId + ".json");

        JsonExportPayload payload = new JsonExportPayload(
                jobId,
                ExportFormat.JSON,
                OffsetDateTime.now(ZoneOffset.UTC),
                initiatedBy,
                documents.size(),
                documents.stream().map(this::toExportDocumentPayload).toList()
        );

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), payload);
        Files.move(tempPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return finalPath;
    }

    private ExportDocumentPayload toExportDocumentPayload(TaxDocument document) {
        return new ExportDocumentPayload(
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
                document.getCurrencyCode(),
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
                document.getProcessingState(),
                document.getDetailLoadedAt(),
                document.getLastSyncedAt(),
                document.getLines().stream()
                        .map(line -> new ExportDocumentLinePayload(
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
                                line.getVatAmount()
                        ))
                        .toList()
        );
    }

    private ExportJobResponse toResponse(ExportJob job) {
        return new ExportJobResponse(
                job.getId(),
                job.getStatus(),
                job.getOutputFormat(),
                job.getDocumentCount(),
                job.getOutputPath(),
                job.getStatus() == JobStatus.COMPLETED ? "/api/v1/exports/" + job.getId() + "/file" : null,
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage()
        );
    }
}
