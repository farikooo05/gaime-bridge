package com.dynorix.gaimebridge.application.port;

import java.util.List;

public record TaxPortalSyncedDocument(
        TaxPortalDocumentSummary summary,
        TaxPortalDocumentDetails details,
        List<TaxPortalDocumentVersion> versions,
        List<TaxPortalDocumentEvent> history,
        TaxPortalDocumentRelationTree relationTree
) {
}
