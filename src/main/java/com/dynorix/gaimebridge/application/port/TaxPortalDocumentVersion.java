package com.dynorix.gaimebridge.application.port;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TaxPortalDocumentVersion(
        String externalVersionId,
        Integer versionNumber,
        boolean current,
        BigDecimal amount,
        BigDecimal vatAmount,
        BigDecimal taxAmount,
        String modifiedBy,
        OffsetDateTime modifiedAt,
        String rawPayload
) {
}
