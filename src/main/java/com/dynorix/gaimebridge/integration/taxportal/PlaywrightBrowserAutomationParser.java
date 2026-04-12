package com.dynorix.gaimebridge.integration.taxportal;

import com.dynorix.gaimebridge.application.port.SyncProgressListener;
import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
import com.dynorix.gaimebridge.integration.taxportal.config.BrowserAutomationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightBrowserAutomationParser {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightBrowserAutomationParser.class);

    private final BrowserAutomationProperties properties;
    private final ObjectMapper objectMapper;

    public PlaywrightBrowserAutomationParser(BrowserAutomationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public RawParseResult login(BrowserPage page, String runtimeUsername, String runtimePassword, SyncProgressListener progressListener) {
        var login = properties.getLogin();
        requireConfigured(login.getUrl(), "app.tax-portal.browser.login.url");
        requireConfigured(login.getUsernameSelector(), "app.tax-portal.browser.login.username-selector");
        requireConfigured(login.getPasswordSelector(), "app.tax-portal.browser.login.password-selector");
        requireConfigured(login.getSubmitSelector(), "app.tax-portal.browser.login.submit-selector");

        String loginUrl = resolveUrl(login.getUrl());
        progressListener.onPhase(SyncPhase.OPENING_LOGIN, "Opening the portal login page.");
        openLoginForm(page, loginUrl, login);
        submitAsanCredentials(page, login, runtimeUsername, runtimePassword);
        log.info("Submitted portal credentials. currentUrl={}", page.currentUrl());
        progressListener.onPhase(SyncPhase.WAITING_FOR_PHONE_CONFIRMATION, "Confirm the Asan request on your phone.");
        waitForVerificationScreen(page, login);
        LoginTransition transition = waitForPostVerificationTransition(page, login);
        if (transition == LoginTransition.TAXPAYER_SELECTION) {
            progressListener.onPhase(SyncPhase.WAITING_FOR_COMPANY_SELECTION, "Choose your company in the portal window.");
            completeTaxpayerSelection(page, login);
        }
        waitForHomeReady(page, login);
        log.info("Portal login flow completed. currentUrl={}", page.currentUrl());

        return new RawParseResult(
                ParsePhase.LOGIN,
                loginUrl,
                page.currentUrl(),
                Instant.now(),
                true,
                page.currentUrl(),
                List.of(),
                Map.of(),
                Map.of(
                        "successSelector", login.getSuccessSelector(),
                        "homeSuccessSelector", login.getHomeSuccessSelector(),
                        "verificationUrl", login.getVerificationUrl(),
                        "chooseTaxpayerPath", login.getChooseTaxpayerPath()),
                page.content(),
                "");
    }

    public boolean hasAuthenticatedSession(BrowserPage page) {
        return isPortalSessionReady(page, properties.getLogin());
    }

    public RawParseResult parseList(BrowserPage page) {
        var list = properties.getList();
        requireConfigured(list.getUrl(), "app.tax-portal.browser.list.url");
        requireConfigured(list.getContainerSelector(), "app.tax-portal.browser.list.container-selector");
        requireConfigured(list.getItemSelector(), "app.tax-portal.browser.list.item-selector");

        String listUrl = resolveUrl(list.getUrl());
        page.navigate(listUrl, properties.getBrowser().getNavigationTimeout());
        page.waitForVisible(list.getContainerSelector(), properties.getBrowser().getTimeout());

        return new RawParseResult(
                ParsePhase.LIST,
                listUrl,
                page.currentUrl(),
                Instant.now(),
                true,
                safeText(page, list.getContainerSelector()),
                page.attributeValues(list.getItemSelector(), list.getDetailLinkAttribute()),
                Map.of(),
                Map.of("itemSelector", list.getItemSelector()),
                page.content(),
                "");
    }

    public RawParseResult parseDetail(BrowserPage page, String detailUrl) {
        var detail = properties.getDetail();
        requireConfigured(detail.getReadySelector(), "app.tax-portal.browser.detail.ready-selector");

        String normalizedUrl = resolveUrl(detailUrl);
        page.navigate(normalizedUrl, properties.getBrowser().getNavigationTimeout());
        page.waitForVisible(detail.getReadySelector(), properties.getBrowser().getTimeout());

        Map<String, String> fields = new TreeMap<>();
        detail.getFieldSelectors().forEach((name, selector) -> fields.put(name, safeText(page, selector)));

        return new RawParseResult(
                ParsePhase.DETAIL,
                normalizedUrl,
                page.currentUrl(),
                Instant.now(),
                true,
                safeText(page, detail.getReadySelector()),
                List.of(),
                fields,
                Map.of(),
                page.content(),
                "");
    }

    private String safeText(BrowserPage page, String selector) {
        return selector == null || selector.isBlank() || !page.isVisible(selector) ? "" : page.text(selector);
    }

    private void openLoginForm(BrowserPage page, String loginUrl, BrowserAutomationProperties.Login login) {
        page.navigate(loginUrl, properties.getBrowser().getNavigationTimeout());
        page.waitForVisible(firstNonBlank(login.getUsernameSelector(), "#phone", "input[name='phone']"), properties.getBrowser().getTimeout());
        page.waitForVisible(firstNonBlank(login.getPasswordSelector(), "#userId", "input[name='userId']"), properties.getBrowser().getTimeout());
    }

    private void submitAsanCredentials(
            BrowserPage page,
            BrowserAutomationProperties.Login login,
            String runtimeUsername,
            String runtimePassword
    ) {
        String resolvedUsername = firstNonBlank(runtimeUsername, login.getUsername());
        String resolvedPassword = firstNonBlank(runtimePassword, login.getPassword());
        requireConfigured(resolvedUsername, "runtime portal phone or app.tax-portal.browser.login.username");
        requireConfigured(resolvedPassword, "runtime portal userId or app.tax-portal.browser.login.password");

        page.fill(firstNonBlank(login.getUsernameSelector(), "#phone", "input[name='phone']"), resolvedUsername);
        page.fill(firstNonBlank(login.getPasswordSelector(), "#userId", "input[name='userId']"), resolvedPassword);
        clickFirstVisible(page, candidateSubmitSelectors(login));
    }

    private void waitForVerificationScreen(BrowserPage page, BrowserAutomationProperties.Login login) {
        long deadline = System.nanoTime() + login.getVerificationTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (isVerificationStageReached(page, login)) {
                log.info("Verification screen or post-verification state reached. currentUrl={}", page.currentUrl());
                return;
            }
            if (!login.getVerificationPendingSelector().isBlank() && page.isVisible(login.getVerificationPendingSelector())) {
                log.info("Verification pending selector is visible. currentUrl={}", page.currentUrl());
                return;
            }
            sleep(login.getPollInterval().toMillis());
        }
        throw new BrowserAutomationParseException("Timed out waiting for Asan verification screen.");
    }

    private LoginTransition waitForPostVerificationTransition(BrowserPage page, BrowserAutomationProperties.Login login) {
        long deadline = System.nanoTime() + login.getVerificationTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (isTaxpayerSelectionScreen(page)) {
                log.info("Tax portal login transitioned to taxpayer selection. url={}", page.currentUrl());
                return LoginTransition.TAXPAYER_SELECTION;
            }
            if (isPortalSessionReady(page, login)) {
                log.info("Tax portal login reached an authenticated portal session. url={}", page.currentUrl());
                return LoginTransition.PORTAL_READY;
            }
            sleep(login.getPollInterval().toMillis());
        }
        throw new BrowserAutomationParseException(
                "Timed out waiting for Asan verification approval. Last URL: " + page.currentUrl());
    }

    private void completeTaxpayerSelection(BrowserPage page, BrowserAutomationProperties.Login login) {
        if (isPortalSessionReady(page, login)) {
            return;
        }
        if (!isTaxpayerSelectionScreen(page)) {
            throw new BrowserAutomationParseException(
                    "Taxpayer selection was expected after verification, but the portal is on an unexpected page: "
                            + page.currentUrl());
        }
        log.info("Waiting for manual taxpayer/company selection. url={}", page.currentUrl());
        waitForManualCompanySelection(page, login);
    }

    private void waitForManualCompanySelection(BrowserPage page, BrowserAutomationProperties.Login login) {
        long deadline = System.nanoTime() + login.getVerificationTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (!isTaxpayerSelectionScreen(page) || isPortalSessionReady(page, login)) {
                log.info("Company selection page completed or portal session is ready. currentUrl={}", page.currentUrl());
                return;
            }
            sleep(login.getPollInterval().toMillis());
        }
        throw new BrowserAutomationParseException(
                "Timed out waiting for manual company selection on the e-portal companies page. Last URL: "
                        + page.currentUrl());
    }

    private void waitForHomeReady(BrowserPage page, BrowserAutomationProperties.Login login) {
        long deadline = System.nanoTime() + login.getVerificationTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (isPortalSessionReady(page, login)) {
                log.info("Authenticated portal session is ready. currentUrl={}", page.currentUrl());
                return;
            }
            sleep(login.getPollInterval().toMillis());
        }
        throw new BrowserAutomationParseException(
                "Timed out waiting for an authenticated e-portal session after taxpayer selection. Last URL: "
                        + page.currentUrl());
    }

    private List<String> candidateSubmitSelectors(BrowserAutomationProperties.Login login) {
        List<String> selectors = new ArrayList<>();
        addIfPresent(selectors, login.getSubmitSelector());
        addIfPresent(selectors, "#loginPageSigninButton");
        addIfPresent(selectors, "button[type='submit']");
        addIfPresent(selectors, "button.ant-btn.ant-btn-primary");
        return selectors;
    }

    private void clickFirstVisible(BrowserPage page, List<String> selectors) {
        for (String selector : selectors) {
            if (selector != null && !selector.isBlank() && page.isVisible(selector)) {
                page.click(selector);
                return;
            }
        }
        throw new BrowserAutomationParseException("No visible login submit button was found on the Asan page.");
    }

    private void addIfPresent(List<String> selectors, String selector) {
        if (selector != null && !selector.isBlank() && !selectors.contains(selector)) {
            selectors.add(selector);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isTaxpayerSelectionScreen(BrowserPage page) {
        String currentUrl = page.currentUrl();
        String content = page.content();
        if (content == null || content.isBlank()) {
            return currentUrl != null && currentUrl.contains("/eportal/verification/companies");
        }
        String normalized = content.toLowerCase();
        if (properties.getLogin().getLegalTin() != null && !properties.getLogin().getLegalTin().isBlank()) {
            normalized = normalized.replace(properties.getLogin().getLegalTin().toLowerCase(), "");
        }
        boolean chooserTextPresent = normalized.contains("axtar")
                || normalized.contains("search");
        boolean configuredTinPresent = properties.getLogin().getLegalTin() != null
                && !properties.getLogin().getLegalTin().isBlank()
                && normalized.contains(properties.getLogin().getLegalTin().toLowerCase());
        return normalized.contains("vergi ödəyicisini seçin")
                || normalized.contains("şəxsi kabinetə daxil olmaq üçün istifadə etmək istədiyiniz vergi ödəyicisini seçin")
                || normalized.contains("vöen 2006765861");
    }

    private void selectTaxpayerFromVisiblePage(BrowserPage page, String legalTin) {
        Object clicked = page.evaluate("""
                ({ legalTin }) => {
                  const selectors = [
                    "a",
                    "[role='button']",
                    "button",
                    "li",
                    "div"
                  ];
                  const nodes = Array.from(document.querySelectorAll(selectors.join(",")));
                  const match = nodes.find(node => {
                    const text = (node.innerText || node.textContent || "").replace(/\\s+/g, " ").trim();
                    return text.includes(legalTin) && text.length < 400;
                  });
                  if (!match) {
                    return false;
                  }
                  const clickable = match.closest("a, [role='button'], button, li, div") || match;
                  clickable.click();
                  for (const selector of ["button.ant-btn-primary", "button[type='submit']", "[role='button'].ant-btn-primary"]) {
                    const button = document.querySelector(selector);
                    if (button && button !== clickable) {
                      button.click();
                      break;
                    }
                  }
                  return true;
                }
                """, Map.of("legalTin", legalTin));
        if (!Boolean.TRUE.equals(clicked)) {
            throw new BrowserAutomationParseException("Visible taxpayer selection screen was shown, but the configured legal TIN could not be clicked.");
        }
        sleep(1000);
    }

    private JsonNode executeJsonRequest(BrowserPage page, String method, String url, String body) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("url", url);
        args.put("method", method);
        args.put("body", body);
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
                    "Login API request failed: " + method + " " + url + " -> HTTP " + envelope.path("status").asInt());
        }
        String responseBody = envelope.path("body").asText("");
        if (responseBody.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new BrowserAutomationParseException("Login API returned non-JSON payload for " + url, ex);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BrowserAutomationParseException("Unable to serialize login request payload.", ex);
        }
    }

    private boolean matchesUrl(String currentUrl, String configuredUrl) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return false;
        }
        if (currentUrl == null || currentUrl.isBlank()) {
            return false;
        }
        String normalizedCurrent = normalizeComparableUrl(currentUrl);
        String normalizedConfigured = normalizeComparableUrl(configuredUrl);
        return normalizedCurrent.equals(normalizedConfigured)
                || normalizedCurrent.startsWith(normalizedConfigured + "?")
                || normalizedCurrent.startsWith(normalizedConfigured + "#");
    }

    private boolean isVerificationStageReached(BrowserPage page, BrowserAutomationProperties.Login login) {
        String currentUrl = page.currentUrl();
        if (matchesUrl(currentUrl, login.getVerificationUrl())) {
            return true;
        }
        if (currentUrl != null) {
            String normalizedCurrent = normalizeUrlFragment(currentUrl);
            if (normalizedCurrent.contains("/eportal/verification")) {
                return true;
            }
        }
        return isTaxpayerSelectionScreen(page) || isPortalSessionReady(page, login);
    }

    private boolean isPortalSessionReady(BrowserPage page, BrowserAutomationProperties.Login login) {
        // 1. BEST CASE: We visually see the dashboard is ready
        if (!login.getHomeSuccessSelector().isBlank() && page.isVisible(login.getHomeSuccessSelector())) {
            return true;
        }
        if (!login.getSuccessSelector().isBlank() && page.isVisible(login.getSuccessSelector())) {
            return true;
        }

        String currentUrl = page.currentUrl();
        if (currentUrl == null || currentUrl.isBlank()) {
            return false;
        }

        String normalizedCurrent = normalizeUrlFragment(currentUrl);
        boolean isEportalUrl = normalizedCurrent.contains("/eportal")
                && !normalizedCurrent.contains("/eportal/login")
                && !normalizedCurrent.contains("/eportal/verification")
                && !isTaxpayerSelectionScreen(page);

        // 2. DOM Check: Ensure we don't return true just because the URL changed
        if (isEportalUrl || matchesUrl(currentUrl, login.getHomeUrl())) {
            if (!login.getHomeSuccessSelector().isBlank() && !page.isVisible(login.getHomeSuccessSelector())) {
                return false;
            }
            return true;
        }

        return false;
    }

    private String normalizeUrlFragment(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeComparableUrl(String value) {
        String normalized = normalizeUrlFragment(value);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            URI uri = URI.create(normalized);
            String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                path += "?" + uri.getRawQuery();
            }
            if (uri.getRawFragment() != null && !uri.getRawFragment().isBlank()) {
                path += "#" + uri.getRawFragment();
            }
            return normalizeUrlFragment(path);
        }
        return normalized;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BrowserAutomationParseException("Interrupted while waiting for Asan login flow.", ex);
        }
    }

    private String resolveUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return pathOrUrl;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        String baseUrl = properties.getBrowser().getBaseUrl();
        if (baseUrl.isBlank()) {
            return pathOrUrl;
        }
        return baseUrl + (pathOrUrl.startsWith("/") ? "" : "/") + pathOrUrl;
    }

    private void requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new BrowserAutomationParseException("Missing required browser property: " + propertyName);
        }
    }

    private enum LoginTransition {
        TAXPAYER_SELECTION,
        PORTAL_READY
    }
}