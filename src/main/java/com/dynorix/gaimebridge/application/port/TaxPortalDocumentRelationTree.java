package com.dynorix.gaimebridge.application.port;

public record TaxPortalDocumentRelationTree(
        String serialNumber,
        String treePayload,
        String parentHistoriesPayload
) {
}
