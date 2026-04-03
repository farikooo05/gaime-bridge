package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SyncJobResponse(
        UUID id,
        JobStatus status,
        SyncPhase phase,
        String phaseMessage,
        Integer documentsDiscovered,
        Integer documentsPersisted,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String errorMessage
) {
}
