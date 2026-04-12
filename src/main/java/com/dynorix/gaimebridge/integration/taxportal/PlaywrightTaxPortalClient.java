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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper objectMapper;

    public PlaywrightTaxPortalClient(
            BrowserAutomationProperties properties,
            PlaywrightBrowserAutomationParser parser,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TaxPortalSyncedDocument> syncDocuments(TaxPortalSyncRequest request, SyncProgressListener progressListener) {
        return withAuthenticatedPage(page -> {
            List<TaxPortalSyncedDocument> results = new ArrayList<>();
            log.info("Starting document sync after authenticated session. currentUrl={} direction={} dateFrom={} dateTo={} loadDetails={}",
                    page.url(), request.direction(), request.dateFrom(), request.dateTo(), request.loadDocumentDetails());

            progressListener.onPhase(com.dynorix.gaimebridge.domain.enumtype.SyncPhase.LOADING_DOCUMENTS, "Portal session is ready. Verifying dashboard...");

            List<TaxPortalDocumentSummary> summaries = fetchDocumentSummaries(page, request);
            log.info("Document summaries fetched. count={} currentUrl={}", summaries.size(), page.url());

            for (TaxPortalDocumentSummary summary : summaries) {
                TaxPortalDocumentDetails details = null;
                List<TaxPortalDocumentVersion> versions = List.of();
                List<TaxPortalDocumentEvent> history = List.of();
                TaxPortalDocumentRelationTree relationTree = null;

                if (request.loadDocumentDetails() && summary.externalDocumentId() != null) {
                    details = fetchDocumentDetails(page, summary.externalDocumentId());
                    
                    try {
                        versions = fetchDocumentVersions(page, summary.externalDocumentId());
                    } catch (Exception ex) {
                        log.warn("Failed to fetch versions for invoice {}, proceeding without them: {}", summary.externalDocumentId(), ex.getMessage());
                    }
                    
                    try {
                        history = fetchDocumentHistory(page, summary.externalDocumentId());
                    } catch (Exception ex) {
                        log.warn("Failed to fetch history for invoice {}, proceeding without it: {}", summary.externalDocumentId(), ex.getMessage());
                    }
                    
                    try {
                        relationTree = fetchDocumentRelationTree(page, summary.externalDocumentId(), buildSerialNumber(summary));
                    } catch (Exception ex) {
                        log.warn("Failed to fetch relation tree for invoice {}, proceeding without it: {}", summary.externalDocumentId(), ex.getMessage());
                    }
                }

                results.add(new TaxPortalSyncedDocument(summary, details, versions, history, relationTree));
            }
            return results;

        }, request, progressListener);
    }

    private void waitForLandingPageReady(Page page) {
        log.info("Waiting for the portal dashboard to stabilize and save the final company token...");
        
        java.util.concurrent.atomic.AtomicReference<String> trueTokenRef = new java.util.concurrent.atomic.AtomicReference<>();
        
        // Intercept the SPA's own requests to capture the true company JWT
        page.onRequest(req -> {
            if (req.url().contains("/profile/public/v2/profile")) {
                String auth = req.headerValue("x-authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    trueTokenRef.set(auth.substring(7).trim());
                    log.info("🎯 CAPTURED THE TRUE COMPANY JWT FROM SPA NETWORK TRAFFIC!");
                }
            }
        });

        try {
            page.waitForSelector("text=Xoş Gəlmişsiniz", new Page.WaitForSelectorOptions().setTimeout(15000));

            long deadline = System.currentTimeMillis() + 15000;
            boolean validTokenFound = false;

            while (System.currentTimeMillis() < deadline) {
                String token = trueTokenRef.get();
                if (token == null || token.isBlank()) {
                    token = readPortalJwt(page);
                }

                if (token != null && !token.isBlank()) {
                    try {
                        page.evaluate("jwt => window.localStorage.setItem('bridge-company-jwt', jwt)", token);
                        executeJsonRequest(page, "GET", properties.getApi().getProfilePath(), null);
                        validTokenFound = true;
                        break;
                    } catch (Exception ex) {
                        log.debug("Token present but API ping failed (likely still the temp token). Waiting...");
                    }
                }
                page.waitForTimeout(1000);
            }

            if (validTokenFound) {
                log.info("Dashboard is steady and company token is verified via API. Proceeding to invoice sync.");
                page.waitForTimeout(500);
            } else {
                log.warn("Could not verify final company token via API within timeout. Proceeding anyway...");
            }

        } catch (Exception e) {
            log.warn("Timeout waiting for landing page stabilization. Proceeding anyway...", e);
            page.waitForTimeout(3000);
        }
    }

    private List<TaxPortalDocumentSummary> fetchDocumentSummaries(Page page, TaxPortalSyncRequest request) {
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
                // For incoming, navigating to the page triggers the find.inbox request on page load
                apiResponse = page.waitForResponse(
                        resp -> resp.url().contains(apiFragment) && resp.request().method().equals("POST"),
                        () -> page.navigate(pageUrl, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE))
                );
            } else {
                // For outgoing, we assume we are already on the page (from the INCOMING pass),
                // so we just click the tab to trigger find.outbox
                apiResponse = page.waitForResponse(
                        resp -> resp.url().contains(apiFragment) && resp.request().method().equals("POST"),
                        () -> {
                            log.info("Clicking 'Göndərilənlər' tab to fetch outgoing invoices");
                            page.locator("text=\"Göndərilənlər\"").click();
                        }
                );
            }

            try {
                String responseBody = apiResponse.text();
                log.info("Intercepted SPA {} response. status={} bodyLength={}", apiFragment, apiResponse.status(), responseBody.length());

                if (apiResponse.status() != 200) {
                    log.error("SPA {} request failed with status {}. body={}", apiFragment, apiResponse.status(), responseBody.substring(0, Math.min(400, responseBody.length())));
                    continue;
                }

                JsonNode responseJson = objectMapper.readTree(responseBody);
                JsonNode invoices = responseJson.path("invoices");
                int invoiceCount = invoices.isArray() ? invoices.size() : 0;
                log.info("Document summaries received via SPA interception. direction={} invoiceCount={}", direction, invoiceCount);

                if (invoices.isArray()) {
                    for (JsonNode invoice : invoices) {
                        sourceRow++;
                        results.add(mapSummary(invoice, direction, sourceRow));
                    }
                }

                // Handle pagination: if the SPA returned a full page, there might be more
                // For now, we handle the first page via SPA navigation; subsequent pages via direct API
                boolean hasMore = responseJson.path("hasMore").asBoolean(false);
                if (hasMore) {
                    log.info("More pages available for direction={}. Fetching remaining pages via API.", direction);
                    int pageNumber = 2;
                    while (hasMore) {
                        String payload = buildListPayload(request, direction, pageNumber);
                        log.info("Fetching document summaries page {}. direction={}", pageNumber, direction);
                        try {
                            JsonNode pageResponse = executeJsonRequest(page, "POST", resolveListPath(direction), payload);
                            JsonNode pageInvoices = pageResponse.path("invoices");
                            if (pageInvoices.isArray()) {
                                for (JsonNode invoice : pageInvoices) {
                                    sourceRow++;
                                    results.add(mapSummary(invoice, direction, sourceRow));
                                }
                            }
                            hasMore = pageResponse.path("hasMore").asBoolean(false);
                            pageNumber++;
                        } catch (Exception ex) {
                            log.warn("Failed to fetch page {} for direction={}. Stopping pagination.", pageNumber, direction, ex);
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

    private TaxPortalDocumentRelationTree fetchDocumentRelationTree(Page page, String externalDocumentId, String serialNumber) {
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

    private <T> T withAuthenticatedPage(Function<Page, T> action, TaxPortalSyncRequest request, SyncProgressListener progressListener) {
        try (Playwright playwright = Playwright.create()) {
            return establishAuthenticatedSession(playwright, request, progressListener, action);
        }
    }

    private <T> T establishAuthenticatedSession(
            Playwright playwright,
            TaxPortalSyncRequest request,
            SyncProgressListener progressListener,
            Function<Page, T> action
    ) {
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
            if (homeUrl != null && !homeUrl.isBlank() && !matchesResolvedUrl(page.url(), homeUrl)) {
                page.navigate(homeUrl);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            }
            return action.apply(page);
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
            SyncProgressListener progressListener
    ) {
        Path storageStatePath = storageStatePath();
        String homeUrl = resolveUrl(properties.getLogin().getHomeUrl());

        if (homeUrl != null && !homeUrl.isBlank()) {
            page.navigate(homeUrl);

            try {
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            } catch (Exception e) {
                log.warn("Network did not idle in time, proceeding to check session state anyway.", e);
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
                log.info("Saved portal session lacks portal JWT. Falling back to interactive login.");
            }
        }

        parser.login(new PlaywrightBrowserPageAdapter(page), request.portalPhone(), request.portalUserId(), progressListener);

        waitForLandingPageReady(page);
        persistStorageState(context);

        if (storageStatePath != null) {
            log.info("Saved authenticated portal storage state to {}", storageStatePath);
        }
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
            log.warn("Unable to inspect portal session state [{}]. currentUrl={} reason={}", label, page.url(), ex.getMessage());
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

    private TaxPortalDocumentSummary mapSummary(JsonNode invoice, DocumentDirection requestedDirection, int sourceRowNumber) {
        DocumentDirection direction = requestedDirection == null ? DocumentDirection.INCOMING : requestedDirection;
        SerialParts serialParts = parseSerialNumber(text(invoice, "serialNumber"));
        JsonNode sender = invoice.path("sender");
        JsonNode receiver = invoice.path("receiver");

        return new TaxPortalDocumentSummary(
                text(invoice, "id"),
                direction,
                serialParts.series(),
                serialParts.number(),
                parseOffsetDateTime(text(invoice, "createdAt")) == null ? null : parseOffsetDateTime(text(invoice, "createdAt")).toLocalDate(),
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
                      xhr.setRequestHeader('X-Authorization', 'Bearer ' + portalJwt);
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
                    "Tax portal API request failed: " + method + " " + url + " -> HTTP " + envelope.path("status").asInt());
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
                  const customToken = window.localStorage.getItem('bridge-company-jwt');
                  if (customToken) return customToken;
                  const defaultToken = window.localStorage.getItem('aztax-jwt');
                  return defaultToken || '';
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

    private static final int PAGE_SIZE = 50;

    private String buildListPayload(TaxPortalSyncRequest request, DocumentDirection direction, int pageNumber) {
        int offset = (pageNumber - 1) * PAGE_SIZE;
        var node = objectMapper.createObjectNode();
        node.put("offset", offset);
        node.put("maxCount", PAGE_SIZE);
        node.put("sortBy", "creationDate");
        node.put("sortAsc", true);

        // Date range in "dd-MM-yyyy HH:mm" format
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        if (request.dateFrom() != null) {
            node.put("creationDateFrom", request.dateFrom().atStartOfDay().format(fmt));
        }
        if (request.dateTo() != null) {
            node.put("creationDateTo", request.dateTo().atStartOfDay().format(fmt));
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
            throw new BrowserAutomationParseException("Interrupted while waiting for e-Qaimə document API readiness.", ex);
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