package com.dynorix.gaimebridge.integration.taxportal;

import com.dynorix.gaimebridge.application.port.SyncProgressListener;
import com.dynorix.gaimebridge.application.port.TaxPortalClient;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentDetails;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentEvent;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentLine;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentRelationTree;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentSummary;
import com.dynorix.gaimebridge.application.port.TaxPortalDocumentVersion;
import com.dynorix.gaimebridge.application.port.TaxPortalSyncRequest;
import com.dynorix.gaimebridge.application.port.TaxPortalSyncedDocument;
import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import com.dynorix.gaimebridge.dto.*;
import com.dynorix.gaimebridge.exception.AuthenticationRequiredException;
import com.dynorix.gaimebridge.integration.taxportal.config.BrowserAutomationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.tax-portal.browser", name = "enabled", havingValue = "true")
public class PlaywrightTaxPortalClient implements TaxPortalClient {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightTaxPortalClient.class);
    private static final ZoneId PORTAL_ZONE = ZoneId.of("Asia/Baku");
    private static final BigDecimal VAT_18 = new BigDecimal("0.18");

    private final BrowserAutomationProperties properties;
    private final PlaywrightBrowserAutomationParser parser;
    private final PlaywrightAuthSessionManager sessionManager;
    private final AuthInterruptionManager interruptionManager;
    private final ObjectMapper objectMapper;

    public PlaywrightTaxPortalClient(
            BrowserAutomationProperties properties,
            PlaywrightBrowserAutomationParser parser,
            PlaywrightAuthSessionManager sessionManager,
            AuthInterruptionManager interruptionManager,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.parser = parser;
        this.sessionManager = sessionManager;
        this.interruptionManager = interruptionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TaxPortalSyncedDocument> syncDocuments(
            TaxPortalSyncRequest request, 
            SyncProgressListener progressListener,
            java.util.function.BooleanSupplier isCancelled) {
        return withAuthenticatedPage(page -> {
            return fetchAndSync(page, request, progressListener, isCancelled);
        }, request, progressListener);
    }

    @Override
    public AuthVerificationResponse startVerification(String portalPhone, String portalUserId) {
        interruptionManager.register(Thread.currentThread(), null);
        Playwright playwright = Playwright.create();
        interruptionManager.updatePlaywright(playwright);
        try {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(properties.getBrowser().isHeadless())
                    .setSlowMo(properties.getBrowser().getSlowMoMs());

            Browser browser = playwright.chromium().launch(launchOptions);
            // Enforce a completely fresh context for new logins to avoid state pollution
            // from saved sessions
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(properties.getBrowser().getUserAgent())
                    .setHttpCredentials("dummy", "dummy"));

            Page page = context.newPage();
            PlaywrightBrowserPageAdapter adapter = new PlaywrightBrowserPageAdapter(page);

            String loginUrl = resolveUrl(properties.getLogin().getUrl());
            page.navigate(loginUrl);

            parser.startInteractiveLogin(adapter, portalPhone, portalUserId,
                    (phase, msg) -> log.info("Auth Phase: {} - {}", phase, msg));

            List<TaxpayerDto> taxpayers = parser.extractAvailableTaxpayers(adapter);
            String sessionId = UUID.randomUUID().toString();

            AuthSessionContext sessionContext = new AuthSessionContext(
                    sessionId, playwright, browser, context, page, adapter, Instant.now(), taxpayers);
            sessionManager.registerSession(sessionContext);

            return new AuthVerificationResponse(sessionId, taxpayers);
        } catch (Exception e) {
            playwright.close();
            throw new BrowserAutomationParseException("Failed to start portal verification", e);
        } finally {
            interruptionManager.unregister();
        }
    }

    @Override
    public void cancelVerification() {
        interruptionManager.cancelAll();
    }

    @Override
    public void logout() {
        log.info("Logging out from portal: clearing saved state.");
        Path path = storageStatePath();
        if (path != null) {
            try {
                Files.deleteIfExists(path);
                log.info("Saved portal state deleted: {}", path);
            } catch (Exception ex) {
                log.warn("Failed to delete saved portal state: {}", ex.getMessage());
            }
        }
    }

    @Override
    public com.dynorix.gaimebridge.dto.AuthStatusResponse checkSession() {
        Path storageStatePath = storageStatePath();
        if (storageStatePath == null || !Files.exists(storageStatePath)) {
            return new com.dynorix.gaimebridge.dto.AuthStatusResponse(false, null, null, null, null);
        }

        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                BrowserContext context = browser.newContext(buildContextOptions())) {

            Page page = context.newPage();
            String homeUrl = resolveUrl(properties.getLogin().getHomeUrl());
            String verificationUrl = (homeUrl != null && !homeUrl.isBlank()) ? homeUrl : resolveUrl("/");

            page.navigate(verificationUrl);
            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            } catch (Exception ignored) {
            }

            String jwt = readPortalJwt(page);
            if (jwt.isBlank()) {
                return new com.dynorix.gaimebridge.dto.AuthStatusResponse(false, null, null, null, null);
            }

            try {
                // Ping the profile API to verify validity
                var profile = executeJsonRequest(page, "GET", properties.getApi().getProfilePath(), null);

                // Parse JWT for metadata
                String[] parts = jwt.split("\\.");
                if (parts.length >= 2) {
                    java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
                    String payload = new String(decoder.decode(parts[1]));
                    JsonNode payloadJson = objectMapper.readTree(payload);

                    String companyName = payloadJson.path("organizationName").asText(null);
                    String tin = payloadJson.path("voen").asText(null);
                    String phone = payloadJson.path("phoneNumber").asText(null);
                    if (phone != null && phone.startsWith("+994")) {
                        phone = phone.substring(4);
                    }
                    long exp = payloadJson.path("exp").asLong(0);
                    OffsetDateTime expiresAt = exp > 0
                            ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(exp), ZoneId.systemDefault())
                            : null;

                    return new com.dynorix.gaimebridge.dto.AuthStatusResponse(true, companyName, tin, expiresAt, phone);
                }

                return new com.dynorix.gaimebridge.dto.AuthStatusResponse(true, "Authenticated", null, null, null);
            } catch (Exception ex) {
                log.warn("Session check failed: {}", ex.getMessage());
                return new com.dynorix.gaimebridge.dto.AuthStatusResponse(false, null, null, null, null);
            }
        } catch (Exception e) {
            log.error("Error checking portal session", e);
            return new com.dynorix.gaimebridge.dto.AuthStatusResponse(false, null, null, null, null);
        }
    }

    @Override
    public void confirmTaxpayer(String sessionId, String legalTin) {
        AuthSessionContext session = sessionManager.getSession(sessionId)
                .orElseThrow(() -> new AuthenticationRequiredException("Session not found or expired"));

        try {
            parser.selectTaxpayer(session.pageAdapter(), legalTin);
            boolean stabilized = waitForLandingPageReady(session.page());

            if (!stabilized) {
                log.error("Failed to capture a valid Company JWT within the stabilization period.");
                throw new AuthenticationRequiredException("Portal failed to issue a company token. Please try again.");
            }

            persistStorageState(session.context());
            log.info("Taxpayer confirmed and session persisted with valid JWT. TIN={}", legalTin);
        } finally {
            sessionManager.closeSession(session);
        }
    }

    private List<TaxPortalSyncedDocument> fetchAndSync(Page page, TaxPortalSyncRequest request,
            SyncProgressListener progressListener,
            java.util.function.BooleanSupplier isCancelled) {
        List<TaxPortalSyncedDocument> results = new ArrayList<>();
        log.info(
                "Starting document sync after authenticated session. currentUrl={} direction={} dateFrom={} dateTo={} loadDetails={}",
                page.url(), request.direction(), request.dateFrom(), request.dateTo(), request.loadDocumentDetails());

        progressListener.onPhase(com.dynorix.gaimebridge.domain.enumtype.SyncPhase.LOADING_DOCUMENTS,
                "Portal session is ready. Verifying dashboard...");

        List<TaxPortalDocumentSummary> summaries = fetchDocumentSummaries(page, request, isCancelled);
        log.info("Document summaries fetched. count={} currentUrl={}", summaries.size(), page.url());

        for (TaxPortalDocumentSummary summary : summaries) {
            TaxPortalDocumentDetails details = null;
            List<TaxPortalDocumentVersion> versions = List.of();
            List<TaxPortalDocumentEvent> history = List.of();
            TaxPortalDocumentRelationTree relationTree = null;

            if (request.loadDocumentDetails() && summary.externalDocumentId() != null) {
                if (isCancelled.getAsBoolean()) {
                    throw new com.dynorix.gaimebridge.exception.SyncCancelledException("Sync cancelled by user during detail loading.");
                }
                details = fetchDocumentDetails(page, summary.externalDocumentId());

                /*
                 * Commented out for now to avoid 403 Forbidden errors on sub-resources
                 * sleep(200); // Rate-limiting safety
                 * 
                 * try {
                 * versions = fetchDocumentVersions(page, summary.externalDocumentId());
                 * } catch (Exception ex) {
                 * log.
                 * warn("Failed to fetch versions for invoice {}, proceeding without them: {}",
                 * summary.externalDocumentId(), ex.getMessage());
                 * }
                 * sleep(200); // Rate-limiting safety
                 * 
                 * try {
                 * history = fetchDocumentHistory(page, summary.externalDocumentId());
                 * } catch (Exception ex) {
                 * log.warn("Failed to fetch history for invoice {}, proceeding without it: {}",
                 * summary.externalDocumentId(), ex.getMessage());
                 * }
                 * sleep(200); // Rate-limiting safety
                 * 
                 * try {
                 * relationTree = fetchDocumentRelationTree(page, summary.externalDocumentId(),
                 * buildSerialNumber(summary));
                 * } catch (Exception ex) {
                 * log.
                 * warn("Failed to fetch relation tree for invoice {}, proceeding without it: {}"
                 * , summary.externalDocumentId(), ex.getMessage());
                 * }
                 */
            }

            results.add(new TaxPortalSyncedDocument(summary, details, versions, history, relationTree));
        }
        return results;
    }

    private boolean waitForLandingPageReady(Page page) {
        log.info("Waiting for the portal dashboard to stabilize and save the final company token...");

        java.util.concurrent.atomic.AtomicReference<String> trueTokenRef = new java.util.concurrent.atomic.AtomicReference<>();

        // Intercept all SPA requests to capture the true company JWT
        page.onRequest(req -> {
            String url = req.url();
            String auth = req.headerValue("x-authorization") == null ? req.headerValue("Authorization")
                    : req.headerValue("x-authorization");
            if (auth != null && auth.startsWith("Bearer ") && auth.length() > 50) {
                log.debug("Found Bearer token in request to: {}", url);
                // We captured a token, but we only "trust" it if it's from a profile or invoice
                // request
                if (url.contains("/profile/") || url.contains("/invoice/") || url.contains("/e-inv/")) {
                    trueTokenRef.set(auth.substring(7).trim());
                    log.info("🎯 Captured a potential Company JWT. Verifying...");
                }
            }
        });

        try {
            long deadline = System.currentTimeMillis() + 25000;
            while (System.currentTimeMillis() < deadline) {
                String currentUrl = page.url();
                log.info("Stabilizing... Current URL: {}", currentUrl);

                String token = trueTokenRef.get();
                if (token == null || token.isBlank()) {
                    token = readPortalJwt(page);
                }

                if (token != null && !token.isBlank()) {
                    try {
                        // Persist the token to localStorage for our own retrieval later
                        page.evaluate("jwt => { window.localStorage.setItem('bridge-company-jwt', jwt); }", token);

                        // Verify the token works by pinging the profile API
                        executeJsonRequest(page, "GET", properties.getApi().getProfilePath(), null);

                        // CRITICAL: Check for cookies but don't strictly block if JWT is verified
                        var cookies = page.context().cookies();
                        if (cookies.isEmpty()) {
                            log.warn(
                                    "Token verified but cookies are still empty. Proceeding with JWT-only session for now.");
                        } else {
                            log.info(
                                    "🎯🎯 DASHBOARD STABLE: Captured and verified the Company JWT and {} cookies. Landing URL: {}",
                                    cookies.size(), currentUrl);
                        }
                        return true;
                    } catch (Exception ex) {
                        log.debug("Token captured but API ping failed. Retrying... URL={} error={}", currentUrl,
                                ex.getMessage());
                    }
                }
                page.waitForTimeout(2000);
            }
        } catch (Exception e) {
            log.warn("Error during landing page stabilization: {}", e.getMessage());
        }
        return false;
    }

    private List<TaxPortalDocumentSummary> fetchDocumentSummaries(Page page, TaxPortalSyncRequest request, java.util.function.BooleanSupplier isCancelled) {
        List<TaxPortalDocumentSummary> results = new ArrayList<>();
        int sourceRow = 0;
        List<DocumentDirection> directions = request.direction() == null
                ? List.of(DocumentDirection.INCOMING, DocumentDirection.OUTGOING)
                : List.of(request.direction());

        for (DocumentDirection direction : directions) {
            String apiFragment = direction == DocumentDirection.OUTGOING ? "find.outbox" : "find.inbox";
            String pageUrl = resolveUrl("/eportal/invoice");

            log.info("Preparing to intercept SPA request. direction={} pageUrl={}", direction, pageUrl);

            com.microsoft.playwright.Response apiResponse;

            if (direction == DocumentDirection.INCOMING) {
                // For incoming, we navigate first, then apply filters if necessary
                page.navigate(pageUrl, new Page.NavigateOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));

                if (shouldApplyFilter(request)) {
                    apiResponse = applyDatesFilter(page, apiFragment, request.dateFrom(), request.dateTo());
                } else {
                    // Failsafe: Add 10s timeout to avoid infinite hang if portal behavior changes
                    try {
                        apiResponse = page.waitForResponse(
                                resp -> resp.url().contains(apiFragment) && resp.request().method().equals("POST"),
                                new Page.WaitForResponseOptions().setTimeout(10000),
                                () -> page.reload(new Page.ReloadOptions()
                                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE)));
                    } catch (com.microsoft.playwright.TimeoutError ex) {
                        log.warn("Timeout waiting for POST response on reload for {}. Attempting fallback...",
                                apiFragment);
                        // If reload didn't trigger it, maybe a simple click or wait works
                        apiResponse = page.waitForResponse(
                                resp -> resp.url().contains(apiFragment) && resp.request().method().equals("POST"),
                                new Page.WaitForResponseOptions().setTimeout(5000),
                                () -> page.waitForTimeout(2000));
                    }
                }
            } else {
                // For outgoing, we assume we are already on the page (from the INCOMING pass)
                log.info("Clicking 'Göndərilənlər' tab to fetch outgoing invoices");
                page.locator("text=\"Göndərilənlər\"").click();
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                page.waitForTimeout(3000); // Increased wait for SPA stability

                if (shouldApplyFilter(request)) {
                    apiResponse = applyDatesFilter(page, apiFragment, request.dateFrom(), request.dateTo());
                } else {
                    // Failsafe: Add 15s timeout
                    try {
                        apiResponse = page.waitForResponse(
                                resp -> resp.url().contains(apiFragment) && resp.request().method().equals("POST"),
                                new Page.WaitForResponseOptions().setTimeout(15000),
                                () -> page.locator("text=\"Göndərilənlər\"").click());
                    } catch (com.microsoft.playwright.TimeoutError ex) {
                        log.warn(
                                "Timeout waiting for POST response for outgoing invoices. Proceeding with last available state.");
                        throw ex; // Re-throw or handle as critical
                    }
                }
            }

            try {
                String responseBody = apiResponse.text();
                log.info("Intercepted SPA {} response. status={} bodyLength={}", apiFragment, apiResponse.status(),
                        responseBody.length());

                if (apiResponse.status() != 200) {
                    log.error("SPA {} request failed with status {}. body={}", apiFragment, apiResponse.status(),
                            responseBody.substring(0, Math.min(400, responseBody.length())));
                    continue;
                }

                JsonNode responseJson = objectMapper.readTree(responseBody);
                JsonNode invoices = responseJson.path("invoices");
                int invoiceCount = invoices.isArray() ? invoices.size() : 0;
                log.info("Document summaries received via SPA interception. direction={} invoiceCount={}", direction,
                        invoiceCount);

                if (invoices.isArray()) {
                    for (JsonNode invoice : invoices) {
                        sourceRow++;
                        String invoiceDate = invoice.path("invoiceDate").asText("Unknown");
                        log.info("[FETCHED] Doc #{} - Date: {} (direction={})", sourceRow, invoiceDate, direction);
                        results.add(mapSummary(invoice, direction, sourceRow));
                    }
                }

                // Handle pagination: use the current result count as the offset for the next
                // page
                boolean hasMore = responseJson.path("hasMore").asBoolean(false);
                if (hasMore) {
                    log.info("More documents available for direction={}. Fetching remaining items via API.", direction);
                    int pageCounter = 2;
                    while (hasMore) {
                        if (isCancelled.getAsBoolean()) {
                            throw new com.dynorix.gaimebridge.exception.SyncCancelledException("Sync cancelled by user during discovery pagination.");
                        }
                        // CRITICAL: use results.size() as the offset to avoid skipping documents
                        // if the first page (SPA) has a different size than PAGE_SIZE.
                        int currentOffset = results.size();
                        String payload = buildListPayloadWithOffset(request, direction, currentOffset);
                        log.info("Fetching document page {} (offset={}) for direction={}", pageCounter, currentOffset,
                                direction);

                        try {
                            JsonNode pageResponse = executeJsonRequest(page, "POST", resolveListPath(direction),
                                    payload);
                            JsonNode pageInvoices = pageResponse.path("invoices");
                            if (pageInvoices.isArray()) {
                                for (JsonNode invoice : pageInvoices) {
                                    sourceRow++;
                                    String invoiceDate = invoice.path("invoiceDate").asText("Unknown");
                                    log.info("[FETCHED] Doc #{} - Date: {} (direction={})", sourceRow, invoiceDate,
                                            direction);
                                    results.add(mapSummary(invoice, direction, sourceRow));
                                }
                            }
                            hasMore = pageResponse.path("hasMore").asBoolean(false);
                            pageCounter++;
                        } catch (Exception ex) {
                            log.warn("Failed to fetch page {} for direction={}. Stopping pagination.", pageCounter,
                                    direction, ex);
                            hasMore = false;
                        }
                    }
                }

            } catch (Exception ex) {
                log.error("Failed to parse SPA {} response.", apiFragment, ex);
            }
        }
        return results;
    }

    private TaxPortalDocumentDetails fetchDocumentDetails(Page page, String externalDocumentId) {
        log.info("Fetching document detail. externalDocumentId={} currentUrl={}", externalDocumentId, page.url());
        return mapDetails(executeJsonRequest(
                page,
                "GET",
                buildDetailPath(externalDocumentId),
                null));
    }

    private List<TaxPortalDocumentVersion> fetchDocumentVersions(Page page, String externalDocumentId) {
        log.info("Fetching document versions. externalDocumentId={} currentUrl={}", externalDocumentId, page.url());
        JsonNode response = executeJsonRequest(page, "GET", buildVersionsPath(externalDocumentId), null);
        JsonNode versions = response.path("versions");
        List<TaxPortalDocumentVersion> results = new ArrayList<>();
        if (versions.isArray()) {
            for (JsonNode version : versions) {
                results.add(new TaxPortalDocumentVersion(
                        text(version, "id"),
                        version.path("versionNumber").isNumber() ? version.path("versionNumber").asInt() : null,
                        version.path("current").asBoolean(false),
                        decimal(version, "amount"),
                        decimal(version, "vatAmount"),
                        decimal(version, "taxAmount"),
                        text(version, "modifiedBy"),
                        parseOffsetDateTime(text(version, "modifiedAt")),
                        toJson(version)));
            }
        }
        return results;
    }

    private List<TaxPortalDocumentEvent> fetchDocumentHistory(Page page, String externalDocumentId) {
        log.info("Fetching document history. externalDocumentId={} currentUrl={}", externalDocumentId, page.url());
        JsonNode response = executeJsonRequest(page, "GET", buildHistoryPath(externalDocumentId), null);
        JsonNode events = response.path("events");
        List<TaxPortalDocumentEvent> results = new ArrayList<>();
        if (events.isArray()) {
            for (JsonNode event : events) {
                results.add(new TaxPortalDocumentEvent(
                        parseOffsetDateTime(text(event, "modifiedAt")),
                        text(event, "userName"),
                        text(event, "status"),
                        text(event, "comment"),
                        text(event, "cancellationReason"),
                        text(event, "documentId"),
                        text(event, "signatureId"),
                        toJson(event)));
            }
        }
        return results;
    }

    private TaxPortalDocumentRelationTree fetchDocumentRelationTree(Page page, String externalDocumentId,
            String serialNumber) {
        log.info("Fetching document relation tree. externalDocumentId={} serialNumber={} currentUrl={}",
                externalDocumentId, serialNumber, page.url());
        JsonNode treeResponse = executeJsonRequest(page, "GET", buildTreePath(externalDocumentId), null);
        String resolvedSerial = serialNumber;
        JsonNode treeNodes = treeResponse.path("invoiceTree");
        if ((resolvedSerial == null || resolvedSerial.isBlank()) && treeNodes.isArray() && treeNodes.size() > 0) {
            resolvedSerial = text(treeNodes.get(0), "serialNumber");
        }
        JsonNode parentResponse = (resolvedSerial == null || resolvedSerial.isBlank())
                ? objectMapper.createObjectNode()
                : executeJsonRequest(page, "GET", buildParentHistoriesPath(resolvedSerial), null);
        return new TaxPortalDocumentRelationTree(
                resolvedSerial,
                toJson(treeResponse),
                toJson(parentResponse));
    }

    private <T> T withAuthenticatedPage(Function<Page, T> action, TaxPortalSyncRequest request,
            SyncProgressListener progressListener) {
        try (Playwright playwright = Playwright.create()) {
            return establishAuthenticatedSession(playwright, request, progressListener, action);
        }
    }

    private <T> T establishAuthenticatedSession(
            Playwright playwright,
            TaxPortalSyncRequest request,
            SyncProgressListener progressListener,
            Function<Page, T> action) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(properties.getBrowser().isHeadless())
                .setSlowMo(properties.getBrowser().getSlowMoMs())
                .setDevtools(!properties.getBrowser().isHeadless());

        try (Browser browser = playwright.chromium().launch(launchOptions);
                BrowserContext context = browser.newContext(buildContextOptions())) {
            Page page = context.newPage();
            ensureAuthenticatedSession(page, context, request, progressListener);
            logSessionState(page, context, "authenticated-session-before-sync");

            String homeUrl = resolveUrl(properties.getLogin().getHomeUrl());
            // If homeUrl is blank, default to base-url to ensure we are on the right origin
            String targetUrl = (homeUrl == null || homeUrl.isBlank()) ? resolveUrl("/") : homeUrl;

            if (!matchesResolvedUrl(page.url(), targetUrl)) {
                page.navigate(targetUrl);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            }

            T result = action.apply(page);

            // Persist state after successful operation to keep tokens fresh
            persistStorageState(context);

            return result;
        }
    }

    private Browser.NewContextOptions buildContextOptions() {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setUserAgent(properties.getBrowser().getUserAgent())
                .setHttpCredentials("dummy", "dummy");

        Path storageStatePath = storageStatePath();
        if (storageStatePath != null && Files.exists(storageStatePath)) {
            log.info("Loading saved portal storage state from {}", storageStatePath);
            options.setStorageStatePath(storageStatePath);
        }
        return options;
    }

    private void ensureAuthenticatedSession(
            Page page,
            BrowserContext context,
            TaxPortalSyncRequest request,
            SyncProgressListener progressListener) {
        Path storageStatePath = storageStatePath();
        String homeUrl = resolveUrl(properties.getLogin().getHomeUrl());
        // CRITICAL: Must navigate to the portal origin first, otherwise localStorage is
        // empty
        String verificationUrl = (homeUrl != null && !homeUrl.isBlank()) ? homeUrl : resolveUrl("/");

        log.info("Verifying portal session at: {}", verificationUrl);
        page.navigate(verificationUrl);

        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        } catch (Exception e) {
            log.warn("Network did not idle in time during verification, checking session state anyway.");
        }

        String portalJwt = readPortalJwt(page);

        if (!portalJwt.isBlank()) {
            log.info("Saved JWT found. Verifying token validity via API Ping...");
            try {
                executeJsonRequest(page, "GET", properties.getApi().getProfilePath(), null);
                log.info("Token is valid. Reusing saved authenticated portal session. currentUrl={}", page.url());
                return;
            } catch (BrowserAutomationParseException ex) {
                if (isUnauthorizedFailure(ex)) {
                    log.warn("Saved token is expired (HTTP 401). Clearing state and forcing fresh login.");
                    page.evaluate("window.localStorage.clear(); window.sessionStorage.clear();");
                    context.clearCookies();
                    page.navigate("about:blank");
                } else {
                    log.warn("Unexpected error verifying session, falling back to login.", ex);
                }
            }
        } else {
            log.info("Saved portal session lacks portal JWT.");
        }

        throw new AuthenticationRequiredException(
                "Portal session is missing or expired. Please re-authenticate via the UI.");
    }

    private void persistStorageState(BrowserContext context) {
        Path storageStatePath = storageStatePath();
        if (storageStatePath == null) {
            return;
        }
        try {
            Path parent = storageStatePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            context.storageState(new BrowserContext.StorageStateOptions().setPath(storageStatePath));
        } catch (Exception ex) {
            throw new BrowserAutomationParseException("Unable to persist portal storage state.", ex);
        }
    }

    private Path storageStatePath() {
        String configured = properties.getBrowser().getStorageStatePath();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return Paths.get(configured);
    }

    private boolean matchesResolvedUrl(String currentUrl, String targetUrl) {
        if (currentUrl == null || currentUrl.isBlank() || targetUrl == null || targetUrl.isBlank()) {
            return false;
        }
        String normalizedCurrent = currentUrl.endsWith("/") ? currentUrl : currentUrl + "/";
        String normalizedTarget = targetUrl.endsWith("/") ? targetUrl : targetUrl + "/";
        return normalizedCurrent.equalsIgnoreCase(normalizedTarget);
    }

    private void logSessionState(Page page, BrowserContext context, String label) {
        try {
            List<String> cookieNames = context.cookies().stream()
                    .map(cookie -> cookie.name)
                    .sorted()
                    .toList();
            Object storageState = page.evaluate("""
                    () => ({
                      localStorageKeys: Object.keys(window.localStorage || {}).sort(),
                      sessionStorageKeys: Object.keys(window.sessionStorage || {}).sort()
                    })
                    """);
            JsonNode storage = objectMapper.valueToTree(storageState);
            List<String> localStorageKeys = readStringList(storage.path("localStorageKeys"));
            List<String> sessionStorageKeys = readStringList(storage.path("sessionStorageKeys"));
            log.info("Portal session state [{}] currentUrl={} cookies={} localStorageKeys={} sessionStorageKeys={}",
                    label, page.url(), cookieNames, localStorageKeys, sessionStorageKeys);
        } catch (Exception ex) {
            log.warn("Unable to inspect portal session state [{}]. currentUrl={} reason={}", label, page.url(),
                    ex.getMessage());
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText("")));
        return values;
    }

    private boolean isUnauthorizedFailure(BrowserAutomationParseException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("HTTP 401");
    }

    private boolean shouldApplyFilter(TaxPortalSyncRequest request) {
        return request.dateFrom() != null || request.dateTo() != null;
    }

    private com.microsoft.playwright.Response applyDatesFilter(Page page, String apiFragment, LocalDate from,
            LocalDate to) {
        log.info("Applying date filters in portal UI: from={} to={}", from, to);
        String applySelector = "button.btn-primary:has-text(\"Filtr tətbiq et\")";

        // 1. Open Filter Drawer (only if not already open)
        if (!page.isVisible(applySelector)) {
            log.info("Filter drawer not visible, clicking 'Filtr' button...");
            try {
                page.click("button:has-text(\"Filtr\")");
                page.waitForSelector(applySelector, new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (Exception ex) {
                log.warn("Failed to open drawer with 'Filtr' text, trying selector alternative...");
                page.click("button.btn-outline-primary:has-text(\"Filtr\")");
                page.waitForSelector(applySelector, new Page.WaitForSelectorOptions().setTimeout(5000));
            }
        }

        // 2. Clear then Fill dates
        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

        if (from != null) {
            String fromVal = from.format(formatter);
            String selector = "input[data-testid='undefinedStart'], input#undefinedStartDate";
            log.debug("Filling start date: {}", fromVal);
            page.click(selector);
            page.locator(selector).fill("");
            page.fill(selector, fromVal);
            page.press(selector, "Enter"); // Restore Enter key to commit the value
            page.waitForTimeout(500);
        }

        if (to != null) {
            String toVal = to.format(formatter);
            String selector = "input[data-testid='undefinedEnd'], input#undefinedEndDate";
            log.debug("Filling end date: {}", toVal);
            page.click(selector);
            page.locator(selector).fill("");
            page.fill(selector, toVal);
            page.press(selector, "Enter"); // Restore Enter key to commit the value
            page.waitForTimeout(500);
        }

        // 3. Apply Filter and Intercept Response
        try {
            return page.waitForResponse(
                    resp -> resp.url().contains(apiFragment) && resp.request().method().equals("POST"),
                    new Page.WaitForResponseOptions().setTimeout(20000),
                    () -> {
                        log.info("Clicking apply filter button (forced): {}", applySelector);
                        page.click(applySelector, new Page.ClickOptions().setForce(true));
                    });
        } catch (com.microsoft.playwright.TimeoutError ex) {
            log.error("Portal failed to respond or button '{}' blocked within timeout", applySelector);
            String content = (String) page.evaluate("() => document.body.innerText.substring(0, 500)", null);
            log.debug("Minimal page context: {}", content);
            throw new BrowserAutomationParseException("Portal timed out while applying date filters.", ex);
        }
    }

    private TaxPortalDocumentSummary mapSummary(JsonNode invoice, DocumentDirection requestedDirection,
            int sourceRowNumber) {
        DocumentDirection direction = requestedDirection == null ? DocumentDirection.INCOMING : requestedDirection;
        SerialParts serialParts = parseSerialNumber(text(invoice, "serialNumber"));
        JsonNode sender = invoice.path("sender");
        JsonNode receiver = invoice.path("receiver");

        return new TaxPortalDocumentSummary(
                text(invoice, "id"),
                direction,
                serialParts.series(),
                serialParts.number(),
                parseOffsetDateTime(text(invoice, "createdAt")) == null ? null
                        : parseOffsetDateTime(text(invoice, "createdAt")).toLocalDate(),
                resolveUrl(buildDetailPath(text(invoice, "id"))),
                parseOffsetDateTime(text(invoice, "createdAt")),
                null,
                null,
                text(invoice, "kind"),
                text(invoice, "type"),
                selectObjectName(direction, sender, receiver),
                invoice.path("version").isMissingNode() || invoice.path("version").isNull()
                        ? null
                        : String.valueOf(invoice.path("version").asInt()),
                null,
                null,
                text(invoice, "status"),
                text(sender, "name"),
                text(sender, "tin"),
                text(receiver, "name"),
                text(receiver, "tin"),
                text(invoice, "invoiceComment"),
                text(invoice, "invoiceComment2"),
                text(invoice, "reason"),
                decimal(invoice, "excise"),
                decimal(invoice, "costWithoutVat"),
                decimal(invoice, "vat"),
                decimal(invoice, "vat18"),
                decimal(invoice, "withoutVat"),
                decimal(invoice, "vatFree"),
                decimal(invoice, "zeroVat"),
                decimal(invoice, "roadTax"),
                null,
                null,
                null,
                decimal(invoice, "amount"),
                sourceRowNumber,
                toJson(invoice));
    }

    private TaxPortalDocumentDetails mapDetails(JsonNode document) {
        JsonNode sender = document.path("sender");
        JsonNode receiver = document.path("receiver");
        JsonNode items = document.path("items");

        List<TaxPortalDocumentLine> lines = new ArrayList<>();
        if (items.isArray()) {
            int index = 0;
            for (JsonNode item : items) {
                index++;
                lines.add(mapLine(item, index));
            }
        }

        return new TaxPortalDocumentDetails(
                text(document, "id"),
                parseOffsetDateTime(text(document, "createdAt")),
                parseOffsetDateTime(text(document, "lastUpdatedAt")),
                firstNonBlank(text(receiver, "objectName"), text(sender, "objectName")),
                document.path("version").isMissingNode() || document.path("version").isNull()
                        ? null
                        : String.valueOf(document.path("version").asInt()),
                text(document, "modificationReason"),
                null,
                null,
                null,
                null,
                text(document, "invoiceComment"),
                text(document, "invoiceComment2"),
                aggregate(items, "excise"),
                aggregateCost(items),
                decimal(document, "vat"),
                aggregate(items, "vat18"),
                null,
                aggregate(items, "exempt"),
                aggregate(items, "vat0"),
                aggregate(items, "roadTax"),
                decimal(document, "amount"),
                toJson(document),
                "",
                lines);
    }

    private TaxPortalDocumentLine mapLine(JsonNode item, int index) {
        BigDecimal taxableAmount = decimal(item, "vat18");
        BigDecimal zeroRatedAmount = decimal(item, "vat0");
        BigDecimal vatExemptAmount = decimal(item, "vatFree");
        BigDecimal exciseAmount = decimal(item, "excise");
        BigDecimal roadTaxAmount = decimal(item, "roadTax");
        BigDecimal vatRate = deriveVatRate(taxableAmount, zeroRatedAmount, vatExemptAmount);
        BigDecimal vatAmount = deriveVatAmount(taxableAmount, vatRate);
        JsonNode productGroup = item.path("productGroup");

        return new TaxPortalDocumentLine(
                index,
                text(item, "itemId"),
                text(productGroup, "code"),
                text(productGroup, "type"),
                text(item, "productName"),
                firstNonBlank(text(productGroup.path("name"), "az"), text(item, "productName")),
                text(item, "barcode"),
                text(item, "unit"),
                decimal(item, "quantity"),
                decimal(item, "pricePerUnit"),
                decimal(item, "cost"),
                taxableAmount,
                zeroRatedAmount,
                vatExemptAmount,
                null,
                exciseAmount,
                roadTaxAmount,
                vatRate,
                vatAmount,
                text(item, "barcode"),
                toJson(item));
    }

    private JsonNode executeJsonRequest(Page page, String method, String pathOrUrl, String requestBody) {
        String url = resolveUrl(pathOrUrl);
        String portalJwt = readPortalJwt(page);
        log.info("Portal API request -> method={} url={} currentUrl={} jwtPresent={}",
                method, url, page.url(), !portalJwt.isBlank());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("url", url);
        args.put("method", method);
        args.put("body", requestBody);
        args.put("portalJwt", portalJwt);

        Object rawResponse = page.evaluate("""
                ({ url, method, body, portalJwt }) => {
                  return new Promise((resolve) => {
                    const xhr = new XMLHttpRequest();
                    xhr.open(method, url, true);
                    xhr.setRequestHeader('Accept', 'application/json, text/plain, */*');
                    if (portalJwt) {
                      // Some portal endpoints want standard Authorization, others want X-Authorization
                      xhr.setRequestHeader('Authorization', 'Bearer ' + portalJwt);
                      xhr.setRequestHeader('X-Authorization', 'Bearer ' + portalJwt);
                      xhr.setRequestHeader('x-client-platform', 'BROWSER');
                    }
                    if (body !== null && body !== undefined && body !== '') {
                      xhr.setRequestHeader('Content-Type', 'application/json');
                    }
                    xhr.withCredentials = true;
                    xhr.onload = function() {
                      resolve({
                        ok: xhr.status >= 200 && xhr.status < 300,
                        status: xhr.status,
                        url: xhr.responseURL,
                        body: xhr.responseText
                      });
                    };
                    xhr.onerror = function() {
                      resolve({
                        ok: false,
                        status: xhr.status || 0,
                        url: url,
                        body: 'XHR network error'
                      });
                    };
                    xhr.send(body || null);
                  });
                }
                """, args);

        JsonNode envelope = objectMapper.valueToTree(rawResponse);
        if (!envelope.path("ok").asBoolean(false)) {
            String responseBody = envelope.path("body").asText("");
            String preview = responseBody.length() > 400 ? responseBody.substring(0, 400) : responseBody;
            log.warn("Portal API request failed -> method={} url={} status={} currentUrl={}",
                    method, url, envelope.path("status").asInt(), page.url());
            log.debug("Portal API failure body preview -> {}", preview);
            throw new BrowserAutomationParseException(
                    "Tax portal API request failed: " + method + " " + url + " -> HTTP "
                            + envelope.path("status").asInt());
        }
        log.info("Portal API response OK -> method={} url={} status={}", method, url, envelope.path("status").asInt());
        String body = envelope.path("body").asText("");
        if (body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new BrowserAutomationParseException("Tax portal API returned non-JSON payload for " + url, ex);
        }
    }

    private String readPortalJwt(Page page) {
        Object token = page.evaluate("""
                () => {
                  if (!window.localStorage) return null;
                  let customToken = window.localStorage.getItem('bridge-company-jwt');
                  if (customToken) return customToken;

                  // Promote default token if found
                  const defaultToken = window.localStorage.getItem('aztax-jwt');
                  if (defaultToken) {
                    window.localStorage.setItem('bridge-company-jwt', defaultToken);
                    return defaultToken;
                  }
                  return '';
                }
                """);
        return token == null ? "" : String.valueOf(token).trim();
    }

    private String resolveListPath(DocumentDirection direction) {
        if (direction == DocumentDirection.OUTGOING) {
            return properties.getApi().getOutboxPath();
        }
        return properties.getApi().getInboxPath();
    }

    private static final int PAGE_SIZE = 20;

    private String buildListPayloadWithOffset(TaxPortalSyncRequest request, DocumentDirection direction, int offset) {
        var node = objectMapper.createObjectNode();
        node.put("offset", offset);
        node.put("maxCount", PAGE_SIZE);
        node.put("sortBy", "creationDate");
        node.put("sortAsc", true);

        // Date range in "dd.MM.yyyy HH:mm" format (dots are critical for this portal's
        // API)
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        if (request.dateFrom() != null) {
            String fromStr = request.dateFrom().atStartOfDay().format(fmt);
            node.put("creationDateFrom", fromStr);
            node.put("dateFrom", fromStr); // Dual-key for different portal versions
        }
        if (request.dateTo() != null) {
            String toStr = request.dateTo().atTime(23, 59, 59).format(fmt);
            node.put("creationDateTo", toStr);
            node.put("dateTo", toStr); // Dual-key for different portal versions
        }

        // Invoice kinds — all known types
        var kinds = node.putArray("kinds");
        for (String k : List.of("defaultInvoice", "agent", "resale", "recycling",
                "taxCodex163", "taxCodex177_5", "returnInvoice", "additionalInvoice")) {
            kinds.add(k);
        }

        // Statuses — all active statuses
        var statuses = node.putArray("statuses");
        for (String s : List.of("approved", "onApproval", "updateApproval", "updateRequested",
                "cancelRequested", "approvedBySystem", "cancelled", "rejected", "draft")) {
            statuses.add(s);
        }

        // Document types
        var types = node.putArray("types");
        types.add("current");
        types.add("corrected");

        // Nullable filter fields
        node.putNull("actionOwner");
        node.putNull("amountFrom");
        node.putNull("amountTo");
        node.putNull("productCode");
        node.putNull("productName");
        node.putNull("receiverName");
        node.putNull("receiverTin");
        node.putNull("senderName");
        node.putNull("senderTin");
        node.putNull("serialNumber");

        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BrowserAutomationParseException("Unable to build list payload", ex);
        }
    }

    private String buildDetailPath(String externalDocumentId) {
        return properties.getApi().getDetailPathTemplate()
                .replace("{id}", externalDocumentId)
                .replace("{sourceSystem}", properties.getApi().getSourceSystem());
    }

    private String buildVersionsPath(String externalDocumentId) {
        return properties.getApi().getVersionsPathTemplate()
                .replace("{id}", externalDocumentId)
                .replace("{sourceSystem}", properties.getApi().getSourceSystem());
    }

    private String buildHistoryPath(String externalDocumentId) {
        return properties.getApi().getHistoryPathTemplate()
                .replace("{id}", externalDocumentId)
                .replace("{sourceSystem}", properties.getApi().getSourceSystem());
    }

    private String buildTreePath(String externalDocumentId) {
        return properties.getApi().getTreePathTemplate()
                .replace("{id}", externalDocumentId)
                .replace("{sourceSystem}", properties.getApi().getSourceSystem());
    }

    private String buildParentHistoriesPath(String serialNumber) {
        return properties.getApi().getParentHistoriesPathTemplate()
                .replace("{serialNumber}", serialNumber == null ? "" : serialNumber)
                .replace("{sourceSystem}", properties.getApi().getSourceSystem());
    }

    private String renderTemplate(String template, Map<String, Object> values) {
        String rendered = template == null || template.isBlank() ? "{}" : template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", toJsonLiteral(entry.getValue()));
        }
        return rendered;
    }

    private String toJsonLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            try {
                return objectMapper.writeValueAsString(stringValue);
            } catch (JsonProcessingException ex) {
                throw new BrowserAutomationParseException("Unable to render JSON template value", ex);
            }
        }
        if (value instanceof LocalDate localDate) {
            return toJsonLiteral(localDate.toString());
        }
        return String.valueOf(value);
    }

    private String resolveUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return pathOrUrl;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        String baseUrl = properties.getBrowser().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return pathOrUrl;
        }
        return baseUrl + (pathOrUrl.startsWith("/") ? "" : "/") + pathOrUrl;
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new BrowserAutomationParseException("Unable to serialize tax portal JSON payload", ex);
        }
    }

    private String text(JsonNode node, String fieldName) {
        return text(node.path(fieldName));
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.asText("");
        return value == null ? "" : value.trim();
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        return decimal(node.path(fieldName));
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String value = node.asText("").trim();
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value).atZone(PORTAL_ZONE).toOffsetDateTime();
    }

    private BigDecimal aggregate(JsonNode items, String fieldName) {
        if (items == null || !items.isArray()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (JsonNode item : items) {
            BigDecimal value = decimal(item, fieldName);
            if (value != null) {
                total = total.add(value);
                found = true;
            }
        }
        return found ? total : null;
    }

    private BigDecimal aggregateCost(JsonNode items) {
        if (items == null || !items.isArray()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (JsonNode item : items) {
            BigDecimal cost = decimal(item, "cost");
            BigDecimal serviceCost = decimal(item, "serviceCost");
            if (cost != null) {
                total = total.add(cost);
                found = true;
            }
            if (serviceCost != null) {
                total = total.add(serviceCost);
                found = true;
            }
        }
        return found ? total : null;
    }

    private BigDecimal deriveVatRate(BigDecimal taxableAmount, BigDecimal zeroRatedAmount, BigDecimal vatExemptAmount) {
        if (taxableAmount != null && taxableAmount.compareTo(BigDecimal.ZERO) > 0) {
            return new BigDecimal("18.0000");
        }
        if (zeroRatedAmount != null && zeroRatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        if (vatExemptAmount != null && vatExemptAmount.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return null;
    }

    private BigDecimal deriveVatAmount(BigDecimal taxableAmount, BigDecimal vatRate) {
        if (taxableAmount == null || vatRate == null || vatRate.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return taxableAmount.multiply(VAT_18).setScale(4, RoundingMode.HALF_UP);
    }

    private String selectObjectName(DocumentDirection direction, JsonNode sender, JsonNode receiver) {
        return direction == DocumentDirection.OUTGOING
                ? firstNonBlank(text(sender, "objectName"), text(receiver, "objectName"))
                : firstNonBlank(text(receiver, "objectName"), text(sender, "objectName"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BrowserAutomationParseException("Interrupted while waiting for e-Qaimə document API readiness.",
                    ex);
        }
    }

    private SerialParts parseSerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return new SerialParts("", "");
        }
        String normalized = serialNumber.trim();
        if (normalized.length() <= 6) {
            return new SerialParts("", normalized);
        }
        return new SerialParts(normalized.substring(0, 6), normalized.substring(6));
    }

    private String buildSerialNumber(TaxPortalDocumentSummary summary) {
        String series = summary.documentSeries() == null ? "" : summary.documentSeries();
        String number = summary.documentNumber() == null ? "" : summary.documentNumber();
        return (series + number).trim();
    }

    private record SerialParts(String series, String number) {
    }
}