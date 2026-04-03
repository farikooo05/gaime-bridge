package com.dynorix.gaimebridge.application.port;

import java.math.BigDecimal;

public record TaxPortalDocumentLine(
        Integer lineNumber,
        String externalLineId,
        String productCode,
        String productType,
        String productName,
        String description,
        String gtin,
        String unitCode,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        BigDecimal taxableAmount,
        BigDecimal zeroRatedAmount,
        BigDecimal vatExemptAmount,
        BigDecimal nonTaxableAmount,
        BigDecimal exciseAmount,
        BigDecimal roadTaxAmount,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        String barcode,
        String rawPayload
) {
}
