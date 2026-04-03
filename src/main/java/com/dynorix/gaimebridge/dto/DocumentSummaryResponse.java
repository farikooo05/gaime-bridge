package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import com.dynorix.gaimebridge.domain.enumtype.DocumentProcessingState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String externalDocumentId,
        DocumentDirection direction,
        String documentSeries,
        String documentNumber,
        LocalDate documentDate,
        OffsetDateTime portalSignedAt,
        String documentTypeName,
        String entryTypeName,
        String portalStatus,
        String sellerName,
        String sellerTaxId,
        String buyerName,
        String buyerTaxId,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String currencyCode,
        DocumentProcessingState processingState
) {
}
