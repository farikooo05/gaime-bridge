package com.dynorix.gaimebridge.application.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.math.BigDecimal;

public record TaxPortalDocumentDetails(
        String externalDocumentId,
        OffsetDateTime portalCreatedAt,
        OffsetDateTime portalUpdatedAt,
        String objectName,
        String documentVersionLabel,
        String amendmentType,
        String relatedDocumentNumber,
        String advanceDocumentSeries,
        String advanceDocumentNumber,
        BigDecimal advanceAmount,
        String baseNote,
        String additionalNote,
        BigDecimal exciseAmount,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal taxableAmount,
        BigDecimal nonTaxableAmount,
        BigDecimal vatExemptAmount,
        BigDecimal zeroRatedAmount,
        BigDecimal roadTaxAmount,
        BigDecimal totalAmount,
        String parsedPayload,
        String htmlSnapshotPath,
        List<TaxPortalDocumentLine> lines
) {
}
