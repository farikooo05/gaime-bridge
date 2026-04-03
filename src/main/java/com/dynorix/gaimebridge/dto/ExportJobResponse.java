package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.ExportFormat;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        JobStatus status,
        ExportFormat outputFormat,
        Integer documentCount,
        String outputPath,
        String downloadUrl,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String errorMessage
) {
}
