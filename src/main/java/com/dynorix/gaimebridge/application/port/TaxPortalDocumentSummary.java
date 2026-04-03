package com.dynorix.gaimebridge.application.port;

import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TaxPortalDocumentSummary(
        String externalDocumentId,
        DocumentDirection direction,
        String documentSeries,
        String documentNumber,
        LocalDate documentDate,
        String portalDetailUrl,
        OffsetDateTime portalCreatedAt,
        OffsetDateTime portalSignedAt,
        OffsetDateTime portalUpdatedAt,
        String documentTypeName,
        String entryTypeName,
        String objectName,
        String documentVersionLabel,
        String amendmentType,
        String relatedDocumentNumber,
        String portalStatus,
        String sellerName,
        String sellerTaxId,
        String buyerName,
        String buyerTaxId,
        String baseNote,
        String additionalNote,
        String reasonText,
        BigDecimal exciseAmount,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal taxableAmount,
        BigDecimal nonTaxableAmount,
        BigDecimal vatExemptAmount,
        BigDecimal zeroRatedAmount,
        BigDecimal roadTaxAmount,
        String advanceDocumentSeries,
        String advanceDocumentNumber,
        BigDecimal advanceAmount,
        BigDecimal totalAmount,
        Integer sourceRowNumber,
        String rawPayload
) {
}
