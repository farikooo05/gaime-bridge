package com.dynorix.gaimebridge.integration.taxportal;

import java.time.Duration;
import java.util.List;

public interface BrowserPage {

    void navigate(String url, Duration timeout);

    void fill(String selector, String value);

    void click(String selector);

    void waitForVisible(String selector, Duration timeout);

    boolean isVisible(String selector);

    String text(String selector);

    List<String> texts(String selector);

    List<String> attributeValues(String selector, String attributeName);

    String currentUrl();

    String content();

    Object evaluate(String script, Object argument);
}
