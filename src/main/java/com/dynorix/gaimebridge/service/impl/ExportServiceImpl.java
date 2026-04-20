package com.dynorix.gaimebridge.service.impl;

import com.dynorix.gaimebridge.config.ExportStorageProperties;
import com.dynorix.gaimebridge.domain.entity.ExportJob;
import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import com.dynorix.gaimebridge.domain.entity.TaxDocumentLine;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.HashMap;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportServiceImpl implements ExportService {

    private final ExportJobRepository exportJobRepository;
    private final TaxDocumentRepository taxDocumentRepository;
    private final ExportStorageProperties exportStorageProperties;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;
    private final Map<String, ColumnDef> columnRegistry = new HashMap<>();

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
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.findAndRegisterModules();
        initializeColumnRegistry();
    }

    private record ColumnDef(String label, BiConsumer<Cell, ExportContext> writer) {}
    private record ExportContext(TaxDocument doc, TaxDocumentLine line, double lineTotal) {}

    private void initializeColumnRegistry() {
        columnRegistry.put("doc_number", new ColumnDef("Sənəd Nömrəsi", (c, ctx) -> c.setCellValue((ctx.doc.getDocumentSeries() != null ? ctx.doc.getDocumentSeries() + " " : "") + ctx.doc.getDocumentNumber())));
        columnRegistry.put("doc_date", new ColumnDef("Tarix", (c, ctx) -> c.setCellValue(ctx.doc.getDocumentDate() != null ? ctx.doc.getDocumentDate().toString() : "")));
        columnRegistry.put("direction", new ColumnDef("İstiqamət", (c, ctx) -> c.setCellValue(ctx.doc.getDirection() != null ? ctx.doc.getDirection().name() : "")));
        columnRegistry.put("doc_type", new ColumnDef("Növ", (c, ctx) -> c.setCellValue(ctx.doc.getDocumentTypeName())));
        columnRegistry.put("status", new ColumnDef("Status", (c, ctx) -> c.setCellValue(ctx.doc.getPortalStatus())));
        columnRegistry.put("seller_tin", new ColumnDef("Satıcı VÖEN", (c, ctx) -> c.setCellValue(ctx.doc.getSeller().getTaxId())));
        columnRegistry.put("seller_name", new ColumnDef("Satıcı Adı", (c, ctx) -> c.setCellValue(ctx.doc.getSeller().getName())));
        columnRegistry.put("buyer_tin", new ColumnDef("Alıcı VÖEN", (c, ctx) -> c.setCellValue(ctx.doc.getBuyer().getTaxId())));
        columnRegistry.put("buyer_name", new ColumnDef("Alıcı Adı", (c, ctx) -> c.setCellValue(ctx.doc.getBuyer().getName())));
        columnRegistry.put("product_name", new ColumnDef("Malın adı", (c, ctx) -> c.setCellValue(ctx.line != null ? ctx.line.getProductName() : "")));
        columnRegistry.put("product_code", new ColumnDef("Məhsulun Kodu", (c, ctx) -> c.setCellValue(ctx.line != null ? ctx.line.getProductCode() : "")));
        columnRegistry.put("gtin", new ColumnDef("GTIN", (c, ctx) -> c.setCellValue(ctx.line != null ? ctx.line.getGtin() : "")));
        columnRegistry.put("description", new ColumnDef("Təsvir", (c, ctx) -> c.setCellValue(ctx.line != null ? ctx.line.getDescription() : "")));
        columnRegistry.put("unit", new ColumnDef("Ölçü Vahidi", (c, ctx) -> c.setCellValue(ctx.line != null ? ctx.line.getUnitCode() : "")));
        columnRegistry.put("quantity", new ColumnDef("Miqdar", (c, ctx) -> c.setCellValue(ctx.line != null && ctx.line.getQuantity() != null ? ctx.line.getQuantity().doubleValue() : 0)));
        columnRegistry.put("price", new ColumnDef("Qiymət", (c, ctx) -> c.setCellValue(ctx.line != null && ctx.line.getUnitPrice() != null ? ctx.line.getUnitPrice().doubleValue() : 0)));
        columnRegistry.put("line_net", new ColumnDef("Məbləğ (ƏDV-siz)", (c, ctx) -> c.setCellValue(ctx.line != null && ctx.line.getLineAmount() != null ? ctx.line.getLineAmount().doubleValue() : 0)));
        columnRegistry.put("vat_rate", new ColumnDef("ƏDV Dərəcəsi", (c, ctx) -> c.setCellValue(ctx.line != null && ctx.line.getVatRate() != null ? ctx.line.getVatRate().doubleValue() : 0)));
        columnRegistry.put("line_vat", new ColumnDef("ƏDV Məbləği", (c, ctx) -> c.setCellValue(ctx.line != null && ctx.line.getVatAmount() != null ? ctx.line.getVatAmount().doubleValue() : 0)));
        columnRegistry.put("line_total", new ColumnDef("Yekun Məbləğ (Sətir)", (c, ctx) -> c.setCellValue(ctx.lineTotal)));
        columnRegistry.put("currency", new ColumnDef("Valyuta", (c, ctx) -> c.setCellValue(ctx.doc.getCurrencyCode())));
        columnRegistry.put("excise", new ColumnDef("Aksiz", (c, ctx) -> c.setCellValue(ctx.doc.getExciseAmount() != null ? ctx.doc.getExciseAmount().doubleValue() : 0)));
        columnRegistry.put("road_tax", new ColumnDef("Yol Vergisi", (c, ctx) -> c.setCellValue(ctx.doc.getRoadTaxAmount() != null ? ctx.doc.getRoadTaxAmount().doubleValue() : 0)));
        columnRegistry.put("portal_id", new ColumnDef("İnvoys ID (Portal)", (c, ctx) -> c.setCellValue(ctx.doc.getExternalDocumentId())));
        columnRegistry.put("related_num", new ColumnDef("Əlaqəli Sənəd №", (c, ctx) -> c.setCellValue(ctx.doc.getRelatedDocumentNumber())));
        columnRegistry.put("amendment", new ColumnDef("Düzəliş Növü", (c, ctx) -> c.setCellValue(ctx.doc.getAmendmentType())));
        columnRegistry.put("base_note", new ColumnDef("Əsas Qeyd", (c, ctx) -> c.setCellValue(ctx.doc.getBaseNote())));
        columnRegistry.put("add_note", new ColumnDef("Əlavə Qeyd", (c, ctx) -> c.setCellValue(ctx.doc.getAdditionalNote())));
        columnRegistry.put("reason", new ColumnDef("Səbəb", (c, ctx) -> c.setCellValue(ctx.doc.getReasonText())));
        columnRegistry.put("object_name", new ColumnDef("Obyekt Adı", (c, ctx) -> c.setCellValue(ctx.doc.getObjectName())));
        columnRegistry.put("tax_taxable", new ColumnDef("Vergi Tutulan Məbləğ", (c, ctx) -> c.setCellValue(ctx.doc.getTaxableAmount() != null ? ctx.doc.getTaxableAmount().doubleValue() : 0)));
        columnRegistry.put("tax_nontaxable", new ColumnDef("Vergi Tutulmayan Məbləğ", (c, ctx) -> c.setCellValue(ctx.doc.getNonTaxableAmount() != null ? ctx.doc.getNonTaxableAmount().doubleValue() : 0)));
        columnRegistry.put("tax_exempt", new ColumnDef("ƏDV-dən Azad Məbləğ", (c, ctx) -> c.setCellValue(ctx.doc.getVatExemptAmount() != null ? ctx.doc.getVatExemptAmount().doubleValue() : 0)));
        columnRegistry.put("tax_zero", new ColumnDef("0 Dərəcəli Məbləğ", (c, ctx) -> c.setCellValue(ctx.doc.getZeroRatedAmount() != null ? ctx.doc.getZeroRatedAmount().doubleValue() : 0)));
        columnRegistry.put("adv_series", new ColumnDef("Avans Seriya", (c, ctx) -> c.setCellValue(ctx.doc.getAdvanceDocumentSeries())));
        columnRegistry.put("adv_number", new ColumnDef("Avans Nömrə", (c, ctx) -> c.setCellValue(ctx.doc.getAdvanceDocumentNumber())));
        columnRegistry.put("adv_amount", new ColumnDef("Avans Məbləği", (c, ctx) -> c.setCellValue(ctx.doc.getAdvanceAmount() != null ? ctx.doc.getAdvanceAmount().doubleValue() : 0)));
        columnRegistry.put("total_amount", new ColumnDef("Cəmi Məbləğ (Sənəd üzrə)", (c, ctx) -> c.setCellValue(ctx.doc.getTotalAmount() != null ? ctx.doc.getTotalAmount().doubleValue() : 0)));
        columnRegistry.put("portal_created", new ColumnDef("Portal Yaradılma", (c, ctx) -> c.setCellValue(ctx.doc.getPortalCreatedAt() != null ? ctx.doc.getPortalCreatedAt().toString() : "")));
        columnRegistry.put("portal_signed", new ColumnDef("Portal İmzalanma", (c, ctx) -> c.setCellValue(ctx.doc.getPortalSignedAt() != null ? ctx.doc.getPortalSignedAt().toString() : "")));
        columnRegistry.put("portal_updated", new ColumnDef("Portal Yenilənmə", (c, ctx) -> c.setCellValue(ctx.doc.getPortalUpdatedAt() != null ? ctx.doc.getPortalUpdatedAt().toString() : "")));
        columnRegistry.put("portal_url", new ColumnDef("Dynorix Brauzer Linki", (c, ctx) -> c.setCellValue(ctx.doc.getPortalDetailUrl())));
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
            List<TaxDocument> documents = taxDocumentRepository.findAllByIdWithLines(requestedDocumentIds).stream()
                    .sorted(Comparator.comparing(TaxDocument::getDocumentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TaxDocument::getDocumentNumber, Comparator.nullsLast(String::compareTo)))
                    .toList();

            if (documents.size() != requestedDocumentIds.size()) {
                job.setStatus(JobStatus.FAILED);
                job.setFinishedAt(OffsetDateTime.now());
                job.setErrorMessage("One or more documents were not found for export");
                return toResponse(exportJobRepository.save(job));
            }

            Path exportPath = switch (request.format()) {
                case JSON -> writeJsonExport(job.getId(), initiatedBy, documents);
                case XML -> writeXmlExport(job.getId(), initiatedBy, documents);
                case XLSX -> writeExcelExport(job.getId(), documents, request.columns());
                default -> throw new IllegalArgumentException("Unsupported export format: " + request.format());
            };

            job.setOutputPath(exportPath.toAbsolutePath().toString());
            job.setStatus(JobStatus.COMPLETED);
            job.setFinishedAt(OffsetDateTime.now());
            return toResponse(exportJobRepository.save(job));
        } catch (IOException | IllegalArgumentException ex) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMessage("Export failed: " + ex.getMessage());
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

    private Path writeXmlExport(UUID jobId, String initiatedBy, List<TaxDocument> documents) throws IOException {
        Path baseDir = Path.of(exportStorageProperties.baseDir()).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);

        Path tempPath = baseDir.resolve("export-" + jobId + ".tmp");
        Path finalPath = baseDir.resolve("export-" + jobId + ".xml");

        JsonExportPayload payload = new JsonExportPayload(
                jobId,
                ExportFormat.XML,
                OffsetDateTime.now(ZoneOffset.UTC),
                initiatedBy,
                documents.size(),
                documents.stream().map(this::toExportDocumentPayload).toList()
        );

        xmlMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), payload);
        Files.move(tempPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return finalPath;
    }

    private Path writeExcelExport(UUID jobId, List<TaxDocument> documents, List<String> requestedColumns) throws IOException {
        Path baseDir = Path.of(exportStorageProperties.baseDir()).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        Path finalPath = baseDir.resolve("export-" + jobId + ".xlsx");

        List<String> columns = (requestedColumns != null && !requestedColumns.isEmpty())
                ? requestedColumns
                : List.of(
                        "doc_number", "doc_date", "direction", "doc_type", "status",
                        "seller_tin", "seller_name", "buyer_tin", "buyer_name",
                        "product_name", "product_code", "gtin", "description",
                        "unit", "quantity", "price", "line_net", "vat_rate",
                        "line_vat", "line_total", "currency", "excise", "road_tax",
                        "portal_id", "related_num", "amendment", "base_note",
                        "add_note", "reason", "object_name", "tax_taxable",
                        "tax_nontaxable", "tax_exempt", "tax_zero", "adv_series",
                        "adv_number", "adv_amount", "total_amount", "portal_created",
                        "portal_signed", "portal_updated", "portal_url"
                );

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Documents");

            // Header Style: Yellow Background, Bold, Center Alignment
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            int headerCol = 0;
            for (String colKey : columns) {
                ColumnDef def = columnRegistry.get(colKey);
                if (def == null) continue;
                Cell cell = headerRow.createCell(headerCol++);
                cell.setCellValue(def.label());
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (TaxDocument doc : documents) {
                var lines = doc.getLines();
                if (lines == null || lines.isEmpty()) {
                    // One row for documents without loaded lines
                    Row row = sheet.createRow(rowIdx++);
                    ExportContext ctx = new ExportContext(doc, null, 0);
                    int targetCol = 0;
                    for (String colKey : columns) {
                        ColumnDef def = columnRegistry.get(colKey);
                        if (def == null) continue;
                        def.writer().accept(row.createCell(targetCol++), ctx);
                    }
                } else {
                    for (var line : lines) {
                        Row row = sheet.createRow(rowIdx++);
                        double lineTotal = (line.getLineAmount() != null ? line.getLineAmount().doubleValue() : 0) + (line.getVatAmount() != null ? line.getVatAmount().doubleValue() : 0);
                        ExportContext ctx = new ExportContext(doc, line, lineTotal);
                        
                        int targetCol = 0;
                        for (String colKey : columns) {
                            ColumnDef def = columnRegistry.get(colKey);
                            if (def == null) continue;
                            def.writer().accept(row.createCell(targetCol++), ctx);
                        }
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            try (var out = java.nio.file.Files.newOutputStream(finalPath)) {
                workbook.write(out);
            }
        }
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
