package com.dynorix.gaimebridge.integration.taxportal;

import com.dynorix.gaimebridge.dto.TaxpayerDto;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthSessionContext(
        String sessionId,
        Playwright playwright,
        Browser browser,
        BrowserContext context,
        Page page,
        PlaywrightBrowserPageAdapter pageAdapter,
        Instant createdAt,
        List<TaxpayerDto> taxpayers
) {
    public void close() {
        try {
            if (context != null) context.close();
        } catch (Exception ignored) {}
        try {
            if (browser != null) browser.close();
        } catch (Exception ignored) {}
        try {
            if (playwright != null) playwright.close();
        } catch (Exception ignored) {}
    }
}
