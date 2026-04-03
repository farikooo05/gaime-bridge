package com.dynorix.gaimebridge.config;

import com.dynorix.gaimebridge.domain.entity.SyncJob;
import com.dynorix.gaimebridge.domain.entity.TaxDocument;
import com.dynorix.gaimebridge.domain.entity.TaxDocumentLine;
import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import com.dynorix.gaimebridge.domain.enumtype.DocumentProcessingState;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.repository.SyncJobRepository;
import com.dynorix.gaimebridge.repository.TaxDocumentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final DemoDataProperties demoDataProperties;
    private final TaxDocumentRepository taxDocumentRepository;
    private final SyncJobRepository syncJobRepository;

    public DemoDataInitializer(
            DemoDataProperties demoDataProperties,
            TaxDocumentRepository taxDocumentRepository,
            SyncJobRepository syncJobRepository
    ) {
        this.demoDataProperties = demoDataProperties;
        this.taxDocumentRepository = taxDocumentRepository;
        this.syncJobRepository = syncJobRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demoDataProperties.enabled() || taxDocumentRepository.count() > 0) {
            return;
        }

        taxDocumentRepository.save(createIncomingEnergyInvoice());
        taxDocumentRepository.save(createIncomingPolymerInvoice());
        taxDocumentRepository.save(createOutgoingDemoInvoice());

        SyncJob syncJob = new SyncJob();
        syncJob.setStatus(JobStatus.COMPLETED);
        syncJob.setRequestedBy("dev");
        syncJob.setFiltersJson("""
                {"direction":"INCOMING","dateFrom":"2026-03-01","dateTo":"2026-03-31","loadDocumentDetails":true}
                """.trim());
        syncJob.setDocumentsDiscovered(3);
        syncJob.setDocumentsPersisted(3);
        syncJob.setStartedAt(OffsetDateTime.now().minusMinutes(3));
        syncJob.setFinishedAt(OffsetDateTime.now().minusMinutes(2));
        syncJobRepository.save(syncJob);
    }

    private TaxDocument createIncomingEnergyInvoice() {
        TaxDocument document = baseDocument(
                "MT260311009900",
                "MT2603",
                "11009900",
                LocalDate.of(2026, 3, 11),
                DocumentDirection.INCOMING,
                "Mallarin, islerin ve xidmetlerin teqdim edilmesi barede elektron qaime-faktura",
                "Cari",
                "Tesdiqlendi",
                "AZERISIQ ACIQ SEHMDAR CEMIYYETI",
                "9900069391",
                "TERMAL PLASTIC MMC",
                "2006765861",
                new BigDecimal("6952.8813"),
                new BigDecimal("1251.5187"),
                "17.02.2026 tarixinden 11.03.2026 tarixinə qeder istifade olunan elektrik enerjisine gore");
        document.setExternalDocumentId("portal-incoming-001");

        TaxDocumentLine line = new TaxDocumentLine();
        line.setLineNumber(1);
        line.setProductName("AKTIV ENERJI");
        line.setProductCode("2716000000");
        line.setGtin("0");
        line.setUnitCode("KVST");
        line.setQuantity(new BigDecimal("77400"));
        line.setUnitPrice(new BigDecimal("0.089830508"));
        line.setLineAmount(new BigDecimal("6952.8813"));
        line.setVatAmount(new BigDecimal("1251.5187"));
        document.addLine(line);
        return document;
    }

    private TaxDocument createIncomingPolymerInvoice() {
        TaxDocument document = baseDocument(
                "MT260310749142",
                "MT2603",
                "10749142",
                LocalDate.of(2026, 3, 10),
                DocumentDirection.INCOMING,
                "Mallarin, islerin ve xidmetlerin teqdim edilmesi barede elektron qaime-faktura",
                "Cari",
                "Tesdiqlendi",
                "BAKI POLIMER ISTEHSALAT MMC",
                "2005263001",
                "TERMAL PLASTIC MMC",
                "2006765861",
                new BigDecimal("45000.0000"),
                new BigDecimal("8100.0000"),
                "Muqavile: BPI25-0117TP");
        document.setExternalDocumentId("portal-incoming-002");

        TaxDocumentLine line = new TaxDocumentLine();
        line.setLineNumber(1);
        line.setProductName("POLIETILEN XAMMALI");
        line.setProductCode("390120");
        line.setUnitCode("KG");
        line.setQuantity(new BigDecimal("2500"));
        line.setUnitPrice(new BigDecimal("18.0000"));
        line.setLineAmount(new BigDecimal("45000.0000"));
        line.setVatAmount(new BigDecimal("8100.0000"));
        document.addLine(line);
        return document;
    }

    private TaxDocument createOutgoingDemoInvoice() {
        TaxDocument document = baseDocument(
                "MT260321000777",
                "MT2603",
                "21000777",
                LocalDate.of(2026, 3, 21),
                DocumentDirection.OUTGOING,
                "Mallarin, islerin ve xidmetlerin teqdim edilmesi barede elektron qaime-faktura",
                "Cari",
                "Sistem terefinden tesdiqlendi",
                "TERMAL PLASTIC MMC",
                "2006765861",
                "QALA-TRIKOTAJ ASC",
                "1200147871",
                new BigDecimal("5410.5085"),
                new BigDecimal("973.8915"),
                "Demo outgoing invoice for local run");
        document.setExternalDocumentId("portal-outgoing-001");

        TaxDocumentLine line = new TaxDocumentLine();
        line.setLineNumber(1);
        line.setProductName("PLASTIC GRANULES");
        line.setProductCode("TG-PL-01");
        line.setUnitCode("KG");
        line.setQuantity(new BigDecimal("320"));
        line.setUnitPrice(new BigDecimal("16.907839"));
        line.setLineAmount(new BigDecimal("5410.5085"));
        line.setVatAmount(new BigDecimal("973.8915"));
        document.addLine(line);
        return document;
    }

    private TaxDocument baseDocument(
            String sourceHash,
            String series,
            String number,
            LocalDate date,
            DocumentDirection direction,
            String documentTypeName,
            String entryTypeName,
            String portalStatus,
            String sellerName,
            String sellerTaxId,
            String buyerName,
            String buyerTaxId,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            String baseNote
    ) {
        TaxDocument document = new TaxDocument();
        document.setDocumentSeries(series);
        document.setDocumentNumber(number);
        document.setDocumentDate(date);
        document.setPortalCreatedAt(date.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
        document.setPortalSignedAt(date.atTime(17, 12).atOffset(OffsetDateTime.now().getOffset()));
        document.setPortalUpdatedAt(date.atTime(18, 0).atOffset(OffsetDateTime.now().getOffset()));
        document.setDirection(direction);
        document.setDocumentTypeName(documentTypeName);
        document.setEntryTypeName(entryTypeName);
        document.setPortalStatus(portalStatus);
        document.setObjectName(direction == DocumentDirection.INCOMING ? "Imported utility invoice" : "Demo outgoing shipment");
        document.setDocumentVersionLabel("v1");
        document.getSeller().setName(sellerName);
        document.getSeller().setTaxId(sellerTaxId);
        document.getBuyer().setName(buyerName);
        document.getBuyer().setTaxId(buyerTaxId);
        document.setNetAmount(netAmount);
        document.setTaxableAmount(netAmount);
        document.setVatAmount(vatAmount);
        document.setTotalAmount(netAmount.add(vatAmount));
        document.setCurrencyCode("AZN");
        document.setBaseNote(baseNote);
        document.setProcessingState(DocumentProcessingState.DETAILS_LOADED);
        document.setDetailLoadedAt(OffsetDateTime.now().minusHours(1));
        document.setLastSyncedAt(OffsetDateTime.now().minusMinutes(5));
        document.getOrCreateSnapshot().setSourceHash(sourceHash);
        document.getOrCreateSnapshot().setParsedPayload("{\"demo\":true}");
        document.getOrCreateSnapshot().setRawPayload("{\"source\":\"demo-seed\"}");
        document.getOrCreateSnapshot().setExtractedAt(OffsetDateTime.now().minusMinutes(5));
        document.getOrCreateSnapshot().setParserVersion("demo-seed");
        return document;
    }
}
