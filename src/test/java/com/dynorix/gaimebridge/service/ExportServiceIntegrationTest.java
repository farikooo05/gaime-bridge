package com.dynorix.gaimebridge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import com.dynorix.gaimebridge.domain.entity.TaxDocumentLine;
import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import com.dynorix.gaimebridge.domain.enumtype.DocumentProcessingState;
import com.dynorix.gaimebridge.domain.enumtype.ExportFormat;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.dto.ExportJobResponse;
import com.dynorix.gaimebridge.dto.ExportRequest;
import com.dynorix.gaimebridge.repository.TaxDocumentRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.exports.base-dir=./build/test-exports")
class ExportServiceIntegrationTest {

    @Autowired
    private ExportService exportService;

    @Autowired
    private TaxDocumentRepository taxDocumentRepository;

    @AfterEach
    void cleanUp() throws Exception {
        taxDocumentRepository.deleteAll();
        Path exportDir = Path.of("./build/test-exports");
        if (Files.exists(exportDir)) {
            try (var paths = Files.list(exportDir)) {
                paths.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
            Files.deleteIfExists(exportDir);
        }
    }

    @Test
    void shouldCreateJsonExportAndResolveFile() throws Exception {
        TaxDocument document = new TaxDocument();
        document.setExternalDocumentId("ext-001");
        document.setDirection(DocumentDirection.INCOMING);
        document.setDocumentSeries("MT2603");
        document.setDocumentNumber("11009900");
        document.setDocumentDate(LocalDate.of(2026, 3, 11));
        document.setDocumentTypeName("Invoice");
        document.setEntryTypeName("Incoming");
        document.setPortalStatus("APPROVED");
        document.getSeller().setName("Azerisiq");
        document.getSeller().setTaxId("9900069391");
        document.getBuyer().setName("Termal Plastic");
        document.getBuyer().setTaxId("2006765861");
        document.setBaseNote("Energy invoice");
        document.setReasonText("Test export");
        document.setCurrencyCode("AZN");
        document.setNetAmount(new BigDecimal("6952.8813"));
        document.setVatAmount(new BigDecimal("1251.5187"));
        document.setTotalAmount(new BigDecimal("8204.4000"));
        document.setProcessingState(DocumentProcessingState.DETAILS_LOADED);
        document.setDetailLoadedAt(OffsetDateTime.now());
        document.setLastSyncedAt(OffsetDateTime.now());

        TaxDocumentLine line = new TaxDocumentLine();
        line.setLineNumber(1);
        line.setProductCode("2716000000");
        line.setProductName("AKTIV ENERJI");
        line.setUnitCode("KVST");
        line.setQuantity(new BigDecimal("77400"));
        line.setUnitPrice(new BigDecimal("0.089830508"));
        line.setLineAmount(new BigDecimal("6952.8813"));
        line.setVatRate(new BigDecimal("18"));
        line.setVatAmount(new BigDecimal("1251.5187"));
        document.replaceLines(List.of(line));

        TaxDocument saved = taxDocumentRepository.save(document);

        ExportJobResponse response = exportService.createExport(
                new ExportRequest(ExportFormat.JSON, List.of(saved.getId())),
                "test-user");

        assertThat(response.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(response.outputPath()).isNotBlank();

        Path exportPath = exportService.resolveExportFile(response.id());
        assertThat(Files.exists(exportPath)).isTrue();
        assertThat(Files.readString(exportPath))
                .contains("\"generatedBy\" : \"test-user\"")
                .contains("\"documentNumber\" : \"11009900\"");
    }
}
