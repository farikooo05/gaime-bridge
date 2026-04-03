package com.dynorix.gaimebridge.integration.taxportal.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tax-portal.browser")
public class BrowserAutomationProperties {

    private boolean enabled;
    private Browser browser = new Browser();
    private Login login = new Login();
    private Api api = new Api();
    private ListView list = new ListView();
    private DetailView detail = new DetailView();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Browser getBrowser() {
        return browser;
    }

    public void setBrowser(Browser browser) {
        this.browser = browser;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public ListView getList() {
        return list;
    }

    public void setList(ListView list) {
        this.list = list;
    }

    public DetailView getDetail() {
        return detail;
    }

    public void setDetail(DetailView detail) {
        this.detail = detail;
    }

    public static class Browser {
        private boolean headless = true;
        private String baseUrl = "";
        private Duration timeout = Duration.ofSeconds(30);
        private Duration navigationTimeout = Duration.ofSeconds(45);
        private double slowMoMs;
        private String userAgent = "gaime-bridge-parser";

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getNavigationTimeout() {
            return navigationTimeout;
        }

        public void setNavigationTimeout(Duration navigationTimeout) {
            this.navigationTimeout = navigationTimeout;
        }

        public double getSlowMoMs() {
            return slowMoMs;
        }

        public void setSlowMoMs(double slowMoMs) {
            this.slowMoMs = slowMoMs;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Login {
        private String url = "";
        private String username = "";
        private String password = "";
        private String usernameSelector = "";
        private String passwordSelector = "";
        private String submitSelector = "";
        private String successSelector = "";
        private String verificationUrl = "";
        private String verificationPendingSelector = "";
        private String verificationStartPath = "";
        private String verificationStatusPath = "";
        private String certificatesPath = "";
        private String chooseTaxpayerPath = "";
        private String ownerType = "legal";
        private String legalTin = "";
        private Duration pollInterval = Duration.ofSeconds(2);
        private Duration verificationTimeout = Duration.ofMinutes(3);
        private String homeUrl = "";
        private String homeSuccessSelector = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUsernameSelector() {
            return usernameSelector;
        }

        public void setUsernameSelector(String usernameSelector) {
            this.usernameSelector = usernameSelector;
        }

        public String getPasswordSelector() {
            return passwordSelector;
        }

        public void setPasswordSelector(String passwordSelector) {
            this.passwordSelector = passwordSelector;
        }

        public String getSubmitSelector() {
            return submitSelector;
        }

        public void setSubmitSelector(String submitSelector) {
            this.submitSelector = submitSelector;
        }

        public String getSuccessSelector() {
            return successSelector;
        }

        public void setSuccessSelector(String successSelector) {
            this.successSelector = successSelector;
        }

        public String getVerificationUrl() {
            return verificationUrl;
        }

        public void setVerificationUrl(String verificationUrl) {
            this.verificationUrl = verificationUrl;
        }

        public String getVerificationPendingSelector() {
            return verificationPendingSelector;
        }

        public void setVerificationPendingSelector(String verificationPendingSelector) {
            this.verificationPendingSelector = verificationPendingSelector;
        }

        public String getVerificationStartPath() {
            return verificationStartPath;
        }

        public void setVerificationStartPath(String verificationStartPath) {
            this.verificationStartPath = verificationStartPath;
        }

        public String getVerificationStatusPath() {
            return verificationStatusPath;
        }

        public void setVerificationStatusPath(String verificationStatusPath) {
            this.verificationStatusPath = verificationStatusPath;
        }

        public String getCertificatesPath() {
            return certificatesPath;
        }

        public void setCertificatesPath(String certificatesPath) {
            this.certificatesPath = certificatesPath;
        }

        public String getChooseTaxpayerPath() {
            return chooseTaxpayerPath;
        }

        public void setChooseTaxpayerPath(String chooseTaxpayerPath) {
            this.chooseTaxpayerPath = chooseTaxpayerPath;
        }

        public String getOwnerType() {
            return ownerType;
        }

        public void setOwnerType(String ownerType) {
            this.ownerType = ownerType;
        }

        public String getLegalTin() {
            return legalTin;
        }

        public void setLegalTin(String legalTin) {
            this.legalTin = legalTin;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public Duration getVerificationTimeout() {
            return verificationTimeout;
        }

        public void setVerificationTimeout(Duration verificationTimeout) {
            this.verificationTimeout = verificationTimeout;
        }

        public String getHomeUrl() {
            return homeUrl;
        }

        public void setHomeUrl(String homeUrl) {
            this.homeUrl = homeUrl;
        }

        public String getHomeSuccessSelector() {
            return homeSuccessSelector;
        }

        public void setHomeSuccessSelector(String homeSuccessSelector) {
            this.homeSuccessSelector = homeSuccessSelector;
        }
    }

    public static class Api {
        private String sourceSystem = "avis";
        private String inboxPath = "/api/po/invoice/public/v2/invoice/find.inbox";
        private String outboxPath = "/api/po/invoice/public/v2/invoice/find.outbox";
        private String draftPath = "/api/po/invoice/public/v2/invoice/find.draft";
        private String detailPathTemplate = "/api/po/invoice/public/v2/invoice/{id}?sourceSystem={sourceSystem}";
        private String versionsPathTemplate = "/api/po/invoice/public/v2/invoice/{id}/versions?sourceSystem={sourceSystem}";
        private String historyPathTemplate = "/api/po/invoice/public/v2/invoice/{id}/history?sourceSystem={sourceSystem}";
        private String treePathTemplate = "/api/po/invoice/public/v2/invoice/{id}/tree?sourceSystem={sourceSystem}";
        private String parentHistoriesPathTemplate = "/api/po/invoice/public/v2/invoice/{serialNumber}/parent-histories?sourceSystem={sourceSystem}";
        private String inboxPayloadTemplate = "{\"page\":{{page}},\"year\":{{year}}}";
        private String outboxPayloadTemplate = "{\"page\":{{page}},\"year\":{{year}}}";
        private String draftPayloadTemplate = "{\"page\":{{page}},\"year\":{{year}}}";

        public String getSourceSystem() {
            return sourceSystem;
        }

        public void setSourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
        }

        public String getInboxPath() {
            return inboxPath;
        }

        public void setInboxPath(String inboxPath) {
            this.inboxPath = inboxPath;
        }

        public String getOutboxPath() {
            return outboxPath;
        }

        public void setOutboxPath(String outboxPath) {
            this.outboxPath = outboxPath;
        }

        public String getDraftPath() {
            return draftPath;
        }

        public void setDraftPath(String draftPath) {
            this.draftPath = draftPath;
        }

        public String getDetailPathTemplate() {
            return detailPathTemplate;
        }

        public void setDetailPathTemplate(String detailPathTemplate) {
            this.detailPathTemplate = detailPathTemplate;
        }

        public String getInboxPayloadTemplate() {
            return inboxPayloadTemplate;
        }

        public void setInboxPayloadTemplate(String inboxPayloadTemplate) {
            this.inboxPayloadTemplate = inboxPayloadTemplate;
        }

        public String getOutboxPayloadTemplate() {
            return outboxPayloadTemplate;
        }

        public void setOutboxPayloadTemplate(String outboxPayloadTemplate) {
            this.outboxPayloadTemplate = outboxPayloadTemplate;
        }

        public String getDraftPayloadTemplate() {
            return draftPayloadTemplate;
        }

        public void setDraftPayloadTemplate(String draftPayloadTemplate) {
            this.draftPayloadTemplate = draftPayloadTemplate;
        }

        public String getVersionsPathTemplate() {
            return versionsPathTemplate;
        }

        public void setVersionsPathTemplate(String versionsPathTemplate) {
            this.versionsPathTemplate = versionsPathTemplate;
        }

        public String getHistoryPathTemplate() {
            return historyPathTemplate;
        }

        public void setHistoryPathTemplate(String historyPathTemplate) {
            this.historyPathTemplate = historyPathTemplate;
        }

        public String getTreePathTemplate() {
            return treePathTemplate;
        }

        public void setTreePathTemplate(String treePathTemplate) {
            this.treePathTemplate = treePathTemplate;
        }

        public String getParentHistoriesPathTemplate() {
            return parentHistoriesPathTemplate;
        }

        public void setParentHistoriesPathTemplate(String parentHistoriesPathTemplate) {
            this.parentHistoriesPathTemplate = parentHistoriesPathTemplate;
        }
    }

    public static class ListView {
        private String url = "";
        private String containerSelector = "";
        private String itemSelector = "";
        private String nextPageSelector = "";
        private String counterpartyNameSelector = "";
        private String counterpartyTaxIdSelector = "";
        private String dateSelector = "";
        private String statusSelector = "";
        private String seriesSelector = "";
        private String numberSelector = "";
        private String totalSelector = "";
        private String vatSelector = "";
        private String typeSelector = "";
        private String entryTypeSelector = "";
        private String noteSelector = "";
        private String detailLinkAttribute = "href";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getContainerSelector() {
            return containerSelector;
        }

        public void setContainerSelector(String containerSelector) {
            this.containerSelector = containerSelector;
        }

        public String getItemSelector() {
            return itemSelector;
        }

        public void setItemSelector(String itemSelector) {
            this.itemSelector = itemSelector;
        }

        public String getNextPageSelector() {
            return nextPageSelector;
        }

        public void setNextPageSelector(String nextPageSelector) {
            this.nextPageSelector = nextPageSelector;
        }

        public String getCounterpartyNameSelector() {
            return counterpartyNameSelector;
        }

        public void setCounterpartyNameSelector(String counterpartyNameSelector) {
            this.counterpartyNameSelector = counterpartyNameSelector;
        }

        public String getCounterpartyTaxIdSelector() {
            return counterpartyTaxIdSelector;
        }

        public void setCounterpartyTaxIdSelector(String counterpartyTaxIdSelector) {
            this.counterpartyTaxIdSelector = counterpartyTaxIdSelector;
        }

        public String getDateSelector() {
            return dateSelector;
        }

        public void setDateSelector(String dateSelector) {
            this.dateSelector = dateSelector;
        }

        public String getStatusSelector() {
            return statusSelector;
        }

        public void setStatusSelector(String statusSelector) {
            this.statusSelector = statusSelector;
        }

        public String getSeriesSelector() {
            return seriesSelector;
        }

        public void setSeriesSelector(String seriesSelector) {
            this.seriesSelector = seriesSelector;
        }

        public String getNumberSelector() {
            return numberSelector;
        }

        public void setNumberSelector(String numberSelector) {
            this.numberSelector = numberSelector;
        }

        public String getTotalSelector() {
            return totalSelector;
        }

        public void setTotalSelector(String totalSelector) {
            this.totalSelector = totalSelector;
        }

        public String getVatSelector() {
            return vatSelector;
        }

        public void setVatSelector(String vatSelector) {
            this.vatSelector = vatSelector;
        }

        public String getTypeSelector() {
            return typeSelector;
        }

        public void setTypeSelector(String typeSelector) {
            this.typeSelector = typeSelector;
        }

        public String getEntryTypeSelector() {
            return entryTypeSelector;
        }

        public void setEntryTypeSelector(String entryTypeSelector) {
            this.entryTypeSelector = entryTypeSelector;
        }

        public String getNoteSelector() {
            return noteSelector;
        }

        public void setNoteSelector(String noteSelector) {
            this.noteSelector = noteSelector;
        }

        public String getDetailLinkAttribute() {
            return detailLinkAttribute;
        }

        public void setDetailLinkAttribute(String detailLinkAttribute) {
            this.detailLinkAttribute = detailLinkAttribute;
        }
    }

    public static class DetailView {
        private String readySelector = "";
        private String lineItemSelector = "";
        private String lineNumberSelector = "";
        private String productNameSelector = "";
        private String productCodeSelector = "";
        private String gtinSelector = "";
        private String unitSelector = "";
        private String quantitySelector = "";
        private String unitPriceSelector = "";
        private String totalSelector = "";
        private Map<String, String> fieldSelectors = new LinkedHashMap<>();

        public String getReadySelector() {
            return readySelector;
        }

        public void setReadySelector(String readySelector) {
            this.readySelector = readySelector;
        }

        public String getLineItemSelector() {
            return lineItemSelector;
        }

        public void setLineItemSelector(String lineItemSelector) {
            this.lineItemSelector = lineItemSelector;
        }

        public String getLineNumberSelector() {
            return lineNumberSelector;
        }

        public void setLineNumberSelector(String lineNumberSelector) {
            this.lineNumberSelector = lineNumberSelector;
        }

        public String getProductNameSelector() {
            return productNameSelector;
        }

        public void setProductNameSelector(String productNameSelector) {
            this.productNameSelector = productNameSelector;
        }

        public String getProductCodeSelector() {
            return productCodeSelector;
        }

        public void setProductCodeSelector(String productCodeSelector) {
            this.productCodeSelector = productCodeSelector;
        }

        public String getGtinSelector() {
            return gtinSelector;
        }

        public void setGtinSelector(String gtinSelector) {
            this.gtinSelector = gtinSelector;
        }

        public String getUnitSelector() {
            return unitSelector;
        }

        public void setUnitSelector(String unitSelector) {
            this.unitSelector = unitSelector;
        }

        public String getQuantitySelector() {
            return quantitySelector;
        }

        public void setQuantitySelector(String quantitySelector) {
            this.quantitySelector = quantitySelector;
        }

        public String getUnitPriceSelector() {
            return unitPriceSelector;
        }

        public void setUnitPriceSelector(String unitPriceSelector) {
            this.unitPriceSelector = unitPriceSelector;
        }

        public String getTotalSelector() {
            return totalSelector;
        }

        public void setTotalSelector(String totalSelector) {
            this.totalSelector = totalSelector;
        }

        public Map<String, String> getFieldSelectors() {
            return fieldSelectors;
        }

        public void setFieldSelectors(Map<String, String> fieldSelectors) {
            this.fieldSelectors = fieldSelectors;
        }
    }
}
