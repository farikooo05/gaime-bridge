package com.dynorix.gaimebridge.application.port;

public interface TaxPortalClient {

    java.util.List<TaxPortalSyncedDocument> syncDocuments(TaxPortalSyncRequest request, SyncProgressListener progressListener);
}
