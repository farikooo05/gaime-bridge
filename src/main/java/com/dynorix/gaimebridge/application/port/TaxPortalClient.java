package com.dynorix.gaimebridge.application.port;

public interface TaxPortalClient {

    java.util.List<TaxPortalSyncedDocument> syncDocuments(
            TaxPortalSyncRequest request, 
            SyncProgressListener progressListener,
            java.util.function.BooleanSupplier isCancelled);

    com.dynorix.gaimebridge.dto.AuthVerificationResponse startVerification(String portalPhone, String portalUserId);

    void confirmTaxpayer(String sessionId, String legalTin);

    com.dynorix.gaimebridge.dto.AuthStatusResponse checkSession();

    void cancelVerification();

    void logout();
}
