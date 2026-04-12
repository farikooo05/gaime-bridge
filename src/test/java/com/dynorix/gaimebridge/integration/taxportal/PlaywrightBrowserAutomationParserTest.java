package com.dynorix.gaimebridge.integration.taxportal;

import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
import com.dynorix.gaimebridge.integration.taxportal.config.BrowserAutomationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaywrightBrowserAutomationParserTest {

    @Test
    void loginWaitsForManualTaxpayerSelectionBeforePortalReady() {
        PlaywrightBrowserAutomationParser parser = new PlaywrightBrowserAutomationParser(parserProperties(), new ObjectMapper());
        FakeBrowserPage page = new FakeBrowserPage(FakeFlow.COMPANY_SELECTION);
        List<SyncPhase> phases = new ArrayList<>();

        parser.login(page, "515000000", "123456", (phase, message) -> phases.add(phase));

        assertEquals(SyncPhase.OPENING_LOGIN, phases.get(0));
        assertEquals(SyncPhase.WAITING_FOR_PHONE_CONFIRMATION, phases.get(1));
        assertEquals(List.of("https://new.e-taxes.gov.az/eportal/login/asan"), page.navigateCalls);
        assertEquals("https://new.e-taxes.gov.az/eportal/", page.currentUrl());
    }

    @Test
    void loginSkipsCompanySelectionWhenPortalSessionIsAlreadyReady() {
        PlaywrightBrowserAutomationParser parser = new PlaywrightBrowserAutomationParser(parserProperties(), new ObjectMapper());
        FakeBrowserPage page = new FakeBrowserPage(FakeFlow.DIRECT_HOME);
        List<SyncPhase> phases = new ArrayList<>();

        parser.login(page, "515000000", "123456", (phase, message) -> phases.add(phase));

        assertEquals(List.of(
                SyncPhase.OPENING_LOGIN,
                SyncPhase.WAITING_FOR_PHONE_CONFIRMATION), phases);
        assertEquals(List.of("https://new.e-taxes.gov.az/eportal/login/asan"), page.navigateCalls);
        assertEquals("https://new.e-taxes.gov.az/eportal/", page.currentUrl());
    }

    private BrowserAutomationProperties parserProperties() {
        BrowserAutomationProperties properties = new BrowserAutomationProperties();
        properties.getBrowser().setBaseUrl("https://new.e-taxes.gov.az");
        properties.getBrowser().setTimeout(Duration.ofMillis(5));
        properties.getBrowser().setNavigationTimeout(Duration.ofMillis(5));

        BrowserAutomationProperties.Login login = properties.getLogin();
        login.setUrl("/eportal/login/asan");
        login.setUsernameSelector("#phone");
        login.setPasswordSelector("#userId");
        login.setSubmitSelector("#loginPageSigninButton");
        login.setVerificationUrl("/eportal/verification/asan");
        login.setLegalTin("2006765861");
        login.setVerificationTimeout(Duration.ofMillis(50));
        login.setPollInterval(Duration.ofMillis(1));
        login.setHomeUrl("/eportal/");
        return properties;
    }

    private enum FakeFlow {
        COMPANY_SELECTION,
        DIRECT_HOME
    }

    private static final class FakeBrowserPage implements BrowserPage {
        private final FakeFlow flow;
        private final List<String> navigateCalls = new ArrayList<>();

        private int stage = 0;
        private int verificationChecks = 0;
        private int companySelectionChecks = 0;

        private FakeBrowserPage(FakeFlow flow) {
            this.flow = flow;
        }

        @Override
        public void navigate(String url, Duration timeout) {
            navigateCalls.add(url);
            stage = 0;
        }

        @Override
        public void fill(String selector, String value) {
        }

        @Override
        public void click(String selector) {
            if ("#loginPageSigninButton".equals(selector)) {
                stage = 1;
            }
        }

        @Override
        public void waitForVisible(String selector, Duration timeout) {
        }

        @Override
        public boolean isVisible(String selector) {
            if (stage == 0) {
                return "#phone".equals(selector) || "#userId".equals(selector) || "#loginPageSigninButton".equals(selector);
            }
            return false;
        }

        @Override
        public String text(String selector) {
            return "";
        }

        @Override
        public List<String> texts(String selector) {
            return List.of();
        }

        @Override
        public List<String> attributeValues(String selector, String attributeName) {
            return List.of();
        }

        @Override
        public String currentUrl() {
            if (stage == 1) {
                verificationChecks++;
                if (flow == FakeFlow.COMPANY_SELECTION && verificationChecks >= 2) {
                    stage = 2;
                } else if (flow == FakeFlow.DIRECT_HOME && verificationChecks >= 2) {
                    stage = 3;
                }
            }
            if (stage == 2 && flow == FakeFlow.COMPANY_SELECTION) {
                companySelectionChecks++;
                if (companySelectionChecks >= 2) {
                    stage = 3;
                }
            }
            return switch (stage) {
                case 1 -> "https://new.e-taxes.gov.az/eportal/verification/asan";
                case 2 -> "https://new.e-taxes.gov.az/eportal/verification/companies";
                case 3 -> "https://new.e-taxes.gov.az/eportal/";
                default -> "https://new.e-taxes.gov.az/eportal/login/asan";
            };
        }

        @Override
        public String content() {
            return stage == 2 ? "vÃ¶en 2006765861" : "";
        }

        @Override
        public Object evaluate(String script, Object argument) {
            return null;
        }
    }
}
