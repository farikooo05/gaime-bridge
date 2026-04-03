package com.dynorix.gaimebridge.application.port;

import java.time.OffsetDateTime;

public record TaxPortalDocumentEvent(
        OffsetDateTime modifiedAt,
        String userName,
        String status,
        String comment,
        String cancellationReason,
        String documentId,
        String signatureId,
        String rawPayload
) {
}
