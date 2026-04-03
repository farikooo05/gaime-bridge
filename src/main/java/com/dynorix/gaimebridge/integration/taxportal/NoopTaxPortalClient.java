package com.dynorix.gaimebridge.integration.taxportal;

import com.dynorix.gaimebridge.application.port.TaxPortalClient;
import com.dynorix.gaimebridge.application.port.SyncProgressListener;
import com.dynorix.gaimebridge.application.port.TaxPortalSyncRequest;
import com.dynorix.gaimebridge.application.port.TaxPortalSyncedDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.tax-portal.browser", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopTaxPortalClient implements TaxPortalClient {

    @Override
    public java.util.List<TaxPortalSyncedDocument> syncDocuments(TaxPortalSyncRequest request, SyncProgressListener progressListener) {
        throw new IllegalStateException("Tax portal browser integration is disabled. Enable app.tax-portal.browser.enabled to run sync.");
    }
}
