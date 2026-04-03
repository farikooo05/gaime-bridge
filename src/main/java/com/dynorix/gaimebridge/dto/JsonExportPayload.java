package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.ExportFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JsonExportPayload(
        UUID exportJobId,
        ExportFormat format,
        OffsetDateTime generatedAt,
        String generatedBy,
        int documentCount,
        List<ExportDocumentPayload> documents
) {
}
