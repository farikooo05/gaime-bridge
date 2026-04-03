package com.dynorix.gaimebridge.application.port;

import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import java.time.LocalDate;

public record TaxPortalSyncRequest(
        String portalPhone,
        String portalUserId,
        DocumentDirection direction,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean loadDocumentDetails
) {
}
