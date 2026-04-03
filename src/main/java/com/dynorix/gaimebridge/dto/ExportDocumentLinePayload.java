package com.dynorix.gaimebridge.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExportDocumentLinePayload(
        UUID id,
        Integer lineNumber,
        String productCode,
        String productName,
        String description,
        String gtin,
        String unitCode,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        BigDecimal vatRate,
        BigDecimal vatAmount
) {
}
