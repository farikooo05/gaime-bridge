package com.dynorix.gaimebridge.integration.taxportal;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import java.time.Duration;
import java.util.List;

public class PlaywrightBrowserPageAdapter implements BrowserPage {

    private final Page page;

    public PlaywrightBrowserPageAdapter(Page page) {
        this.page = page;
    }

    @Override
    public void navigate(String url, Duration timeout) {
        page.navigate(url, new Page.NavigateOptions().setTimeout(timeout.toMillis()));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    @Override
    public void fill(String selector, String value) {
        page.locator(selector).fill(value);
    }

    @Override
    public void click(String selector) {
        page.locator(selector).click();
    }

    @Override
    public void waitForVisible(String selector, Duration timeout) {
        page.locator(selector).waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(timeout.toMillis()));
    }

    @Override
    public boolean isVisible(String selector) {
        return page.locator(selector).count() > 0 && page.locator(selector).first().isVisible();
    }

    @Override
    public String text(String selector) {
        return page.locator(selector).first().innerText().trim();
    }

    @Override
    public List<String> texts(String selector) {
        return page.locator(selector).allInnerTexts().stream().map(String::trim).toList();
    }

    @Override
    public List<String> attributeValues(String selector, String attributeName) {
        Object result = page.locator(selector).evaluateAll(
                "(elements, attr) => elements.map(element => element.getAttribute(attr)).filter(Boolean)",
                attributeName);
        if (result instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @Override
    public String currentUrl() {
        return page.url();
    }

    @Override
    public String content() {
        return page.content();
    }

    @Override
    public Object evaluate(String script, Object argument) {
        return page.evaluate(script, argument);
    }
}
