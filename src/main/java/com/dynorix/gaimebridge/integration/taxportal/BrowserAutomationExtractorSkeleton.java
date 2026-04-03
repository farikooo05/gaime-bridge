package com.dynorix.gaimebridge.integration.taxportal;

import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BrowserAutomationExtractorSkeleton {

    private final PlaywrightBrowserAutomationParser parser;

    public BrowserAutomationExtractorSkeleton(PlaywrightBrowserAutomationParser parser) {
        this.parser = parser;
    }

    public List<RawParseResult> extract(BrowserPage page) {
        List<RawParseResult> results = new ArrayList<>();
        results.add(parser.login(page, null, null, (phase, message) -> {
            if (phase == SyncPhase.FAILED && message != null) {
                throw new IllegalStateException(message);
            }
        }));
        RawParseResult list = parser.parseList(page);
        results.add(list);
        for (String item : list.items()) {
            if (item != null && !item.isBlank()) {
                results.add(parser.parseDetail(page, item));
            }
        }
        return List.copyOf(results);
    }
}
