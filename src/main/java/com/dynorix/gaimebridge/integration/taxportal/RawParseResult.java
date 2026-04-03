package com.dynorix.gaimebridge.integration.taxportal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RawParseResult(
        ParsePhase phase,
        String requestedUrl,
        String finalUrl,
        Instant capturedAt,
        boolean success,
        String primaryText,
        List<String> items,
        Map<String, String> fields,
        Map<String, String> metadata,
        String htmlContent,
        String errorMessage
) {
}
