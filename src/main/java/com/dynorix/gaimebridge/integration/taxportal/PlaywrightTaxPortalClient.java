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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.tax-portal.browser", name = "enabled", havingValue = "true")
public class PlaywrightTaxPortalClient implements TaxPortalClient {

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
            progressListener.onPhase(com.dynorix.gaimebridge.domain.enumtype.SyncPhase.LOADING_DOCUMENTS, "Portal session is ready. Loading documents from e-Qaimə.");
            List<TaxPortalDocumentSummary> summaries = fetchDocumentSummaries(page, request);
            for (TaxPortalDocumentSummary summary : summaries) {
                TaxPortalDocumentDetails details = null;
                List<TaxPortalDocumentVersion> versions = List.of();
                List<TaxPortalDocumentEvent> history = List.of();
                TaxPortalDocumentRelationTree relationTree = null;

                if (request.loadDocumentDetails() && summary.externalDocumentId() != null) {
                    details = fetchDocumentDetails(page, summary.externalDocumentId());
                    versions = fetchDocumentVersions(page, summary.externalDocumentId());
                    history = fetchDocumentHistory(page, summary.externalDocumentId());
                    relationTree = fetchDocumentRelationTree(page, summary.externalDocumentId(), buildSerialNumber(summary));
                }

                results.add(new TaxPortalSyncedDocument(summary, details, versions, history, relationTree));
            }
            return results;
        }, request, progressListener);
    }

    private List<TaxPortalDocumentSummary> fetchDocumentSummaries(Page page, TaxPortalSyncRequest request) {
        List<TaxPortalDocumentSummary> results = new ArrayList<>();
        int sourceRow = 0;
        List<DocumentDirection> directions = request.direction() == null
                ? List.of(DocumentDirection.INCOMING, DocumentDirection.OUTGOING)
                : List.of(request.direction());

        for (DocumentDirection direction : directions) {
            int pageNumber = 1;
            boolean hasMore;
            do {
                JsonNode response = executeJsonRequest(
                        page,
                        "POST",
                        resolveListPath(direction),
                        buildListPayload(request, direction, pageNumber));
                JsonNode invoices = response.path("invoices");
                if (invoices.isArray()) {
                    for (JsonNode invoice : invoices) {
                        sourceRow++;
                        results.add(mapSummary(invoice, direction, sourceRow));
                    }
                }
                hasMore = response.path("hasMore").asBoolean(false);
                pageNumber++;
            } while (hasMore);
        }
        return results;
    }

    private TaxPortalDocumentDetails fetchDocumentDetails(Page page, String externalDocumentId) {
        return mapDetails(executeJsonRequest(
                page,
                "GET",
                buildDetailPath(externalDocumentId),
                null));
    }

    private List<TaxPortalDocumentVersion> fetchDocumentVersions(Page page, String externalDocumentId) {
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
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(properties.getBrowser().isHeadless())
                    .setSlowMo(properties.getBrowser().getSlowMoMs());

            try (Browser browser = playwright.chromium().launch(launchOptions);
                 BrowserContext context = browser.newContext(
                         new Browser.NewContextOptions().setUserAgent(properties.getBrowser().getUserAgent()))) {
                Page page = context.newPage();
                parser.login(new PlaywrightBrowserPageAdapter(page), request.portalPhone(), request.portalUserId(), progressListener);
                return action.apply(page);
            }
        }
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
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("url", url);
        args.put("method", method);
        args.put("body", requestBody);

        Object rawResponse = page.evaluate("""
                async ({ url, method, body }) => {
                  const options = {
                    method,
                    credentials: 'include',
                    headers: {
                      'Accept': 'application/json'
                    }
                  };
                  if (body !== null && body !== undefined && body !== '') {
                    options.headers['Content-Type'] = 'application/json';
                    options.body = body;
                  }
                  const response = await fetch(url, options);
                  const text = await response.text();
                  return {
                    ok: response.ok,
                    status: response.status,
                    url: response.url,
                    body: text
                  };
                }
                """, args);

        JsonNode envelope = objectMapper.valueToTree(rawResponse);
        if (!envelope.path("ok").asBoolean(false)) {
            throw new BrowserAutomationParseException(
                    "Tax portal API request failed: " + method + " " + url + " -> HTTP " + envelope.path("status").asInt());
        }
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

    private String resolveListPath(DocumentDirection direction) {
        if (direction == DocumentDirection.OUTGOING) {
            return properties.getApi().getOutboxPath();
        }
        return properties.getApi().getInboxPath();
    }

    private String buildListPayload(TaxPortalSyncRequest request, DocumentDirection direction, int pageNumber) {
        String template = direction == DocumentDirection.OUTGOING
                ? properties.getApi().getOutboxPayloadTemplate()
                : properties.getApi().getInboxPayloadTemplate();
        int year = request.dateFrom() != null ? request.dateFrom().getYear() : LocalDate.now(PORTAL_ZONE).getYear();
        return renderTemplate(template, Map.of(
                "page", pageNumber,
                "year", year,
                "dateFrom", request.dateFrom(),
                "dateTo", request.dateTo(),
                "sourceSystem", properties.getApi().getSourceSystem()));
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
            throw new BrowserAutomationParseException("Interrupted while waiting for e-QaimÉ™ document API readiness.", ex);
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
